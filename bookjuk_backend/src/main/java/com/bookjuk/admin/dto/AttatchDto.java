package com.bookjuk.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AttatchDto {
  
  private Integer attatchId;
  private String filePath;
  private String originalFilename;
  private String filesystemName;
  private Integer productId;

}
