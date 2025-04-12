package com.bookjuk.port.one.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter

public class CompletePaymentRequest {
  private String paymentId;
}
