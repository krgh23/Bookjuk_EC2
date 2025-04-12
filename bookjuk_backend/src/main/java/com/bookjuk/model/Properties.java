package com.bookjuk.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

@Configuration
@Getter
public class Properties {
  
  public static String portoneSecretApi;
  public static String portoneSecretWebhook;
  
  public static String naverClientId;
  public static String naverClientSecret;
  public static String imgbbApiKey;
  
  @Value("${portone.secret.api}")
  public void setPortoneSecretApi(String portoneSecretApi) {
    System.out.println("----------------------setPortoneSecretApi----------------------------");
    System.out.println(portoneSecretApi);
    Properties.portoneSecretApi = portoneSecretApi;
  }

  @Value("${portone.secret.webhook}")
  public void setPortoneSecretWebhook(String portoneSecretWebhook) {
    System.out.println("----------------------setPortoneSecretWebhook----------------------------");
    System.out.println(portoneSecretWebhook);
    Properties.portoneSecretWebhook = portoneSecretWebhook;
  }

  


  @Value("${Naver.client-id}")
  public void setNaverClientId(String naverClientId) {
    Properties.naverClientId = naverClientId;
  }

  @Value("${Naver.client-secret}")
  public void setNaverClientSecret(String naverClientSecret) {
    Properties.naverClientSecret = naverClientSecret;
  }

  @Value("${imgbb.app.key}")
  public void setImgbbApiKey(String imgbbApiKey) {
    Properties.imgbbApiKey = imgbbApiKey;
  }


}
