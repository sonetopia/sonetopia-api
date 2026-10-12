package com.sonetopia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sonetopia.entity.PlaylistAudio;

public interface PlaylistAudioRepository extends JpaRepository<PlaylistAudio, Integer>, JpaSpecificationExecutor<PlaylistAudio> {

  Optional<PlaylistAudio> findFirstByPlaylist_IdOrderByPositionDesc(UUID playlistId);

  Optional<PlaylistAudio> findFirstByPlaylist_IdAndAudio_Id(UUID playlistId, UUID audioId);

  List<PlaylistAudio> findByPlaylist_Id(UUID playlistId);

  List<PlaylistAudio> findByPlaylist_IdOrderByPositionAsc(UUID playlistId);
}
