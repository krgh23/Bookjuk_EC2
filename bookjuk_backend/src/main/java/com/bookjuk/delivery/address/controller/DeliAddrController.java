package com.bookjuk.delivery.address.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bookjuk.delivery.address.dto.DeliAddrDto;
import com.bookjuk.delivery.address.service.IDeliAddrService;
import com.bookjuk.model.message.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
@Tag(name = "배송", description = "배송 API")
public class DeliAddrController {

  private final IDeliAddrService deliAddrService;

  /**
   * (1) 특정 userId의 배송지 조회
   */
  @Operation(summary = "배송지 조회", description = "특정 userId의 배송지 조회를 하는 기능입니다.")
  @GetMapping(value = "/deliAddr/user/{userId}", produces = "application/json")
  public ResponseMessage getDeliAddrByUserId(@PathVariable("userId") Integer userId) {
    List<DeliAddrDto> deliAddrDto = deliAddrService.findDeliAddrByUserId(userId);
    return ResponseMessage.builder()
        .status(200)
        .message("배송지 조회 성공")
        .results(Map.of("deliAddr", deliAddrDto))
        .build();
  }

  /**
   * (2) 배송지 추가
   * 
   * {
   * "addrId": 3,
   * "product": {
   * "productId": 1
   * },
   * "productQuantity": 5
   * }
   */
  @Operation(summary = "배송지 추가", description = "배송지 추가를 하는 기능입니다.")
  @PostMapping(value = "/deliAddr", produces = "application/json")
  public ResponseMessage createDeliAddr(@RequestBody DeliAddrDto deliAddrDto) {
    DeliAddrDto newDeliAddrDto = deliAddrService.insertDeliAddr(deliAddrDto);
    return ResponseMessage.builder()
        .status(200)
        .message("배송지 추가 성공")
        .results(Map.of("deliAddr", newDeliAddrDto))
        .build();
  }

  /**
   * (3) 배송지 수정(주소변경)
   */
  @Operation(summary = "배송지 수정", description = "주소 변경을 하는 기능입니다")
  @PutMapping(value = "/deliAddr/{addrId}", produces = "application/json")
  public ResponseMessage updateDeliAddr(
      @PathVariable("addrId") Integer addrId,
      @RequestBody DeliAddrDto deliAddrDto
  ) {
    deliAddrDto.setAddrId(addrId);
    DeliAddrDto updated = deliAddrService.updateDeliAddr(deliAddrDto);

    return ResponseMessage.builder()
        .status(200)
        .message("배송지 수정 성공")
        .results(Map.of("deliAddr", updated))
        .build();
  }

  /**
   * (4) 배송지 삭제
   */
  @Operation(summary = "배송지 삭제", description = "배송지 삭제를 하는 기능입니다")
  @DeleteMapping(value = "/deliAddr/{addrId}", produces = "application/json")
  public ResponseMessage deleteDeliAddr(
      @PathVariable("addrId") Integer addrId
  ) {
    deliAddrService.deleteDeliAddr(addrId);
    return ResponseMessage.builder()
        .status(200)
        .message("배송지 아이템 삭제 성공")
        .build();
  }

}
