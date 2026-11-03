package com.isocode.township.matching;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankImportRepository extends JpaRepository<BankImport, Long> {

    List<BankImport> findAllByOrderByUploadedAtDescIdDesc();

    List<BankImport> findByFileSha256OrderByUploadedAtAsc(String fileSha256);
}
