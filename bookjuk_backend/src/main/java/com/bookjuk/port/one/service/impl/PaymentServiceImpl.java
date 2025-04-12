package com.bookjuk.port.one.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import com.bookjuk.cart.service.ICartService;
import com.bookjuk.model.Properties;
import com.bookjuk.order.domain.Order;
import com.bookjuk.order.domain.User;
import com.bookjuk.order.dto.OrderDto;
import com.bookjuk.order.dto.OrderItemDto;
import com.bookjuk.order.repository.OrderRepository;
import com.bookjuk.port.one.Payment;
import com.bookjuk.port.one.PaymentCustomData;
import com.bookjuk.port.one.PaymentInfo;
import com.bookjuk.port.one.PaymentPaymentInfo;
import com.bookjuk.port.one.repository.PayRepository;
import com.bookjuk.port.one.repository.ProductRepository;
import com.bookjuk.port.one.request.SyncPaymentException;
import com.bookjuk.port.one.service.IPaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.payment.CancelPaymentBodyRefundAccount;
import io.portone.sdk.server.payment.CancelRequester;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.payment.PromotionDiscountRetainOption;
import io.portone.sdk.server.payment.VirtualAccountIssuedPayment;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookVerifier;
import jakarta.annotation.PostConstruct;
import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

  private final OrderRepository orderRepository;
  private final ModelMapper modelMapper;
  // 결제 정보를 저장하는 임시 메모리(실제 환경에서는 데이터베이스를 사용해야 함)
  private static final Map<String, Payment> paymentStore = new HashMap<>();
  private final ObjectMapper objectMapper;

  // 서비스 의존성 (장바구니 서비스 및 상품 리포지토리)
  private final ICartService iCartService;
  private final ProductRepository productRepository;
  private final PayRepository payRepository;

  private final Properties properties;
  private PaymentClient portone;
  private WebhookVerifier portoneWebhook;

  @PostConstruct
  public void init() {
    log.info("**************(0) PortOne Secret API: {}", properties.portoneSecretApi);
    log.info("**************(0) PortOne Secret Webhook: {}", properties.portoneSecretWebhook);
    this.portone = new PaymentClient(
        properties.portoneSecretApi,
        "https://api.portone.io",
        
        null
    );
    this.portoneWebhook = new WebhookVerifier(
        properties.portoneSecretWebhook
    );
  }
  
  /**
   * 결제 완료 요청(브라우저 호출 또는 웹훅 호출)을 받아 결제 상태를 동기화합니다.
   * 서버의 결제 데이터베이스와 포트원의 결제 정보를 비교하여, 결제 검증 후 상태를 업데이트합니다.
   */
  @Override
  public Mono<Payment> syncPayment(String paymentId) {
    log.info("**************(1) syncPayment 시작. paymentId: {}", paymentId);
    Payment payment = paymentStore.get(paymentId);
    if (payment == null) {
      payment = new Payment("PENDING");
      paymentStore.put(paymentId, payment);
      log.info("**************(2) 신규 결제 상태 생성. paymentId: {}, status: {}", paymentId, "PENDING");
    }
    Payment finalPayment = payment;

    return Mono.fromFuture(portone.getPayment(paymentId))
        .doOnSuccess(actualPayment -> log.info("**************(3) PortOne API 응답 수신. paymentId: {}, 실제 응답 타입: {}", paymentId, actualPayment.getClass().getSimpleName()))
        .onErrorMap(e -> {
          log.error("**************(4) PortOne API 호출 실패. paymentId: {}. 원인: {}", paymentId, e.toString());
          return new SyncPaymentException();
        })
        .flatMap(actualPayment -> {
          if (actualPayment instanceof PaidPayment) {
            PaidPayment paidPayment = (PaidPayment) actualPayment;
            log.info("**************(5) PaidPayment 타입 확인. paymentId: {}", paymentId);
            if (!verifyPayment(paidPayment)) {
              log.info("**************(6) 검증 실패. 자동 결제 취소 진행. paymentId: {}", paymentId);
              // 검증 실패 시 결제를 취소
              return cancelPayment(paymentId, "검증 실패로 인한 자동 취소", null)
                  .flatMap(cancelResponse -> {
                    log.error("**************(7) 결제 취소 완료 후에도 검증 실패 처리. paymentId: {}", paymentId);
                    return Mono.error(new SyncPaymentException());
                  });
            }
            log.info("**************(8) 결제 성공. paymentId: {}", paymentId);
            if (finalPayment.status().equals("PAID")) {
              return Mono.just(finalPayment);
            } else {
              Payment newPayment = new Payment("PAID");
              paymentStore.put(paymentId, newPayment);
              log.info("**************(9) 결제 상태 업데이트(PAID). paymentId: {}", paymentId);
              return Mono.just(newPayment);
            }
          } else if (actualPayment instanceof VirtualAccountIssuedPayment) {
            log.info("**************(10) 가상계좌 발급 응답 수신. paymentId: {}", paymentId);
            Payment newPayment = new Payment("VIRTUAL_ACCOUNT_ISSUED");
            paymentStore.put(paymentId, newPayment);
            return Mono.just(newPayment);
          } else {
            log.info("**************(11) 기타 응답 처리. paymentId: {}", paymentId);
            return Mono.just(finalPayment);
          }
        });
  }

  /**
   * 결제 취소 로직을 분리한 메서드입니다.
   */
  public Mono<?> cancelPayment(String paymentId, String reason, Long cancelAmount) {
    log.info("**************(12) cancelPayment 호출. paymentId: {}, reason: {}, cancelAmount: {}", paymentId, reason, cancelAmount);
    return Mono.fromFuture(
        portone.cancelPayment(
            paymentId,
            (Long) cancelAmount, // 결제 취소 금액 (null이면 전액 취소)
            (Long) null, // 면세 금액
            (Long) null, // 부가세 금액
            reason,
            (CancelRequester) null,
            (PromotionDiscountRetainOption) null,
            (Long) null, // 현재 취소 가능 금액
            (CancelPaymentBodyRefundAccount) null
        )
    ).doOnSuccess(cancelResponse -> {
        log.info("**************(13) cancelPayment 성공. paymentId: {}", paymentId);
    }).doOnError(e -> {
        log.error("**************(14) cancelPayment 실패. paymentId: {}, error: {}", paymentId, e.getMessage());
    });
  }

  /**
   * 웹훅 요청을 처리하여 결제 상태를 동기화합니다.
   */
  @Override
  public Mono<Unit> handleWebhook(
      String body, String webhookId, String webhookTimestamp, String webhookSignature
  ) throws SyncPaymentException {
    log.info("**************(15) handleWebhook 호출. webhookId: {}", webhookId);
    Webhook webhook;
    try {
      webhook = portoneWebhook.verify(body, webhookId, webhookSignature, webhookTimestamp);
      log.info("**************(16) 웹훅 검증 성공. webhookId: {}", webhookId);
    } catch (Exception e) {
      log.error("**************(17) 웹훅 검증 실패. webhookId: {}, error: {}", webhookId, e.getMessage());
      throw new SyncPaymentException();
    }
    if (webhook instanceof WebhookTransaction transaction) {
      return syncPayment(transaction.getData().getPaymentId())
          .map(payment -> Unit.INSTANCE);
    }
    return Mono.empty();
  }

  /**
   * 결제 승인 정보와 결제 항목의 검증을 진행합니다.
   */
  public boolean verifyPayment(PaidPayment payment) {
    log.info("**************(18) verifyPayment 시작. paymentId: {}", payment.getId());
    String customDataJson = payment.getCustomData();
    if (customDataJson == null) {
      log.error("**************(19) customDataJson null. paymentId: {}", payment.getId());
      return false;
    }

    PaymentCustomData customDataDecoded;
    try {
      customDataDecoded = objectMapper.readValue(customDataJson, PaymentCustomData.class);
      log.info("**************(20) customDataDecoded 성공. paymentId: {}", payment.getId());
    } catch (JsonProcessingException e) {
      log.error("**************(21) customDataDecoded 실패. paymentId: {}, error: {}", payment.getId(), e.getMessage());
      return false;
    }

    int computedSum = 0;
    for (PaymentPaymentInfo item : customDataDecoded.item()) {
      PaymentInfo info = item.paymentInfo();
      Integer pid;
      try {
        pid = info.productId();
      } catch (NumberFormatException e) {
        log.error("**************(22) productId 변환 실패. paymentId: {}", payment.getId());
        return false;
      }
      Optional<com.bookjuk.port.one.domain.Product> productOpt = productRepository.findById(pid);
      if (!productOpt.isPresent()) {
        log.error("**************(23) product 미존재. productId: {}", pid);
        return false;
      }
      com.bookjuk.port.one.domain.Product product = productOpt.get();
      if (info.quantity() > product.getStock()) {
        log.error("**************(24) 재고 부족. productId: {}, quantity: {}, stock: {}", pid, info.quantity(), product.getStock());
        return false;
      }
      if (info.price() != product.getProductPrice()) {
        log.error("**************(25) 가격 불일치. productId: {}, price: {}, DB 가격: {}", pid, info.price(), product.getProductPrice());
        return false;
      }
      if (product.getSalesYn() == null || product.getSalesYn() != 'Y') {
        log.error("**************(26) 판매 불가 상품. productId: {}", pid);
        return false;
      }
      computedSum += info.quantity() * info.price();
    }

    if (payment.getAmount().getTotal() != computedSum) {
      log.error("**************(27) 결제 총액 불일치. paymentId: {}, 요청 총액: {}, 계산 총액: {}", payment.getId(), payment.getAmount().getTotal(), computedSum);
      return false;
    }
    if (!"KRW".equals(payment.getCurrency().getValue())) {
      log.error("**************(28) 결제 통화 오류. paymentId: {}, 통화: {}", payment.getId(), payment.getCurrency().getValue());
      return false;
    }

    log.info("**************(29) 결제 검증 통과. paymentId: {}", payment.getId());
    // 검증 후 상품 수량 변경, 장바구니 삭제, 주문 생성 및 Pay 생성 등 후처리 진행
    for (PaymentPaymentInfo item : customDataDecoded.item()) {
      PaymentInfo info = item.paymentInfo();
      com.bookjuk.port.one.domain.Product product = productRepository.findById(info.productId()).get();
      product.setStock(product.getStock() - info.quantity());
      productRepository.save(product);
      log.info("**************(30) 상품 재고 차감. productId: {}, newStock: {}", info.productId(), product.getStock());
    }
    for (PaymentPaymentInfo item : customDataDecoded.item()) {
      PaymentInfo info = item.paymentInfo();
      iCartService.deleteCartItem(info.cartItemId());
      log.info("**************(31) 장바구니 아이템 삭제. cartItemId: {}", info.cartItemId());
    }
    List<OrderItemDto> orderItemList = new ArrayList<>();
    int userId = 0, shippingAddressId = 0, totalPrice = 0;
    boolean totalPriceSet = false;
    for (PaymentPaymentInfo item : customDataDecoded.item()) {
      PaymentInfo info = item.paymentInfo();
      if (userId == 0) {
        userId = info.userId();
        shippingAddressId = info.shippingAddressId();
      }
      if (!totalPriceSet) {
        totalPrice = info.total();
        totalPriceSet = true;
      }
      OrderItemDto orderItemDto = OrderItemDto.builder()
          .product(com.bookjuk.order.domain.Product.builder().productId(info.productId()).build())
          .orderStatus(com.bookjuk.order.domain.OrderStatus.builder().orderStatusId(1).build())
          .quantity(info.quantity())
          .price(info.price())
          .build();
      orderItemList.add(orderItemDto);
      log.info("**************(32) 주문 항목 추가. productId: {}, quantity: {}", info.productId(), info.quantity());
    }
    OrderDto orderDto = OrderDto.builder()
        .user(new User(userId))
        .deliAddr(com.bookjuk.order.domain.DeliAddr.builder().addrId(shippingAddressId).build())
        .totalPrice(totalPrice)
        .orderItems(orderItemList)
        .build();
    OrderDto createdOrder = insertOrder(userId, orderDto);
    if (createdOrder != null && createdOrder.getOrderId() != null) {
      Order order = new Order();
      order.setOrderId(createdOrder.getOrderId());
      String paymentIdFromData = customDataDecoded.item().get(0).paymentInfo().paymentId();
      com.bookjuk.port.one.domain.Pay pay = com.bookjuk.port.one.domain.Pay.builder()
          .order(order)
          .impUid(paymentIdFromData)
          .amount(createdOrder.getTotalPrice())
          .payStatus("결제완료")
          .build();
      payRepository.save(pay);
      log.info("**************(33) Pay 객체 생성 및 DB 저장. orderId: {}, paymentId: {}", createdOrder.getOrderId(), paymentIdFromData);
    }
    return true;
  }

  /**
   * 새로운 주문을 생성하는 메서드.
   */
  @Override
  public OrderDto insertOrder(Integer userId, OrderDto orderDto) {
    if (userId == null) {
      throw new IllegalArgumentException("유저 아이디가 제공되지 않아 주문을 진행할 수 없습니다.");
    }
    Order orderEntity = modelMapper.map(orderDto, Order.class);
    if (orderEntity.getOrderItems() != null) {
      orderEntity.getOrderItems().forEach(item -> item.setOrder(orderEntity));
    }
    Order savedOrder = orderRepository.save(orderEntity);
    OrderDto savedOrderDto = modelMapper.map(savedOrder, OrderDto.class);
    log.info("**************(34) 주문 생성 완료. orderId: {}", savedOrderDto.getOrderId());
    return savedOrderDto;
  }
}
