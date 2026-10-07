package com.sonetopia.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "users")
public class User {
  
  @Id 
  @UuidGenerator 
  private UUID id;
  
  private String username;

  private String email;

  private String password;
  
  @Column(name = "storage_used")
  private Long storageUsed;
  
  @Column(name = "is_admin")
  private Boolean isAdmin;
  
  @Column(name = "created_at")
  private LocalDateTime createdAt;
  
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "user")
  private List<Audio> audios;

  @OneToMany(mappedBy = "user")
  private List<Playlist> playlists;
}
