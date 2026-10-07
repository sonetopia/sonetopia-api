package com.sonetopia.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sonetopia.entity.Audio;

public interface AudioRepository extends JpaRepository<Audio, UUID>, JpaSpecificationExecutor<Audio> {

}
