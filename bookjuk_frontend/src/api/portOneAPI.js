/**
 * order 관련 API 호출 모음
 *
 * Developer : 조범희
 */

import { portOneApi } from './baseApi';

/**
 * 결제 완료 요청 보내기 (POST)
 * 포트원 결제 후, 결제 완료 상태를 서버에 전달합니다.
 *
 * @param {String} paymentId - 포트원에서 발급한 결제 고유 ID
 * @returns {Promise<Object>} - API 응답 데이터 (예: { status: "PAID" } 등)
 */
export const completePayment = async (paymentId) => {
  const response = await portOneApi.post(`/payment/complete`, { paymentId });
  return response.data;
};
