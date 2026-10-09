package com.sonetopia.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sonetopia.entity.Playlist;
import com.sonetopia.entity.User;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID>, JpaSpecificationExecutor<Playlist> {

  Optional<Playlist> findByIdAndUser(UUID id, User user);
}
