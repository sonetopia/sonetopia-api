package com.sonetopia.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter 
@Getter 
@AllArgsConstructor 
@NoArgsConstructor 
@Entity 
@Table(name = "audio")
public class Audio {

  @Id 
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private User user;
  
  private String title;
  
  private String type;
  
  private String author;
  
  private String album;
  
  private Short year;
  
  @Column(name = "cover_storage_key")
  private String coverStorageKey;

  @Column(name = "cover_mime_type")
  private String coverMimeType;

  @Column(name = "cover_file_size")
  private Long coverFileSize;

  @Column(name = "cover_version")
  private Integer coverVersion = 0;

  @Column(name = "storage_key")
  private String storageKey;
  
  @Column(name = "mime_type")
  private String mimeType;
  
  @Column(name = "file_size")
  private Long fileSize;

  private Integer duration;
  
  @Column(name = "created_at")
  private LocalDateTime createdAt;
  
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
  
  @OneToMany(mappedBy = "audio")
  private List<PlaylistAudio> playlistAudios;
}
