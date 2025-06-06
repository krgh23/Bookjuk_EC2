package com.bookjuk.product.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder

@Entity(name = "product_publisher")
@Table(name = "tbl_publisher")
public class Publisher {
  
  @Id
  @Column(name = "publisher_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer publisherId;
  
  @Column(name = "publisher_name")
  private String publisherName;
  
  @Column(name = "website")
  private String website;
  
  
  

}
