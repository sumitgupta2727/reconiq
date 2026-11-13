package com.reconiq.repository;

import com.reconiq.entity.UploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

    List<UploadBatch> findAllByOrderByUploadedAtDesc();
}
