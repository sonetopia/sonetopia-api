package com.sonetopia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;

public interface AudioRepository extends JpaRepository<Audio, UUID>, JpaSpecificationExecutor<Audio> {

  Optional<Audio> findByIdAndUser(UUID id, User user);
  
  List<Audio> findByUser(User user);
}
