package com.orbit.repository;

import com.orbit.domain.config.SlackProjectChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SlackProjectChannelRepository extends JpaRepository<SlackProjectChannel, Long> {
    Optional<SlackProjectChannel> findByProjectId(Long projectId);
}
