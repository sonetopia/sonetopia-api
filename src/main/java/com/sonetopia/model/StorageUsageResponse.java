package com.sonetopia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StorageUsageResponse {

  private Long usedBytes;

  private Long maxBytes;

  private Long remainingBytes;

  private Boolean unlimited;
}