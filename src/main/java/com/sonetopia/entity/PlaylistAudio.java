package com.sonetopia.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter 
@Getter 
@AllArgsConstructor 
@NoArgsConstructor 
@Entity 
@Table(
  name = "playlist_audio",
  uniqueConstraints = {
    @UniqueConstraint(
      name = "uq_playlist_audio_playlist_audio",
      columnNames = {"playlist_id", "audio_id"}
    )
  }
)
public class PlaylistAudio {

  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "playlist_id", referencedColumnName = "id")
  private Playlist playlist;
  
  @ManyToOne
  @JoinColumn(name = "audio_id", referencedColumnName = "id")
  private Audio audio;
  
  private Integer position;
}
