package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvImportResponseDto;
import io.github.mainalisandeep.cvgen.dto.GithubRepositoryResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Brings existing material into the editor: an old CV file, or public GitHub repositories as projects.
 * Both return drafts; neither writes anything.
 */
public interface CvImportService {

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when the file is not a
     *                                                                            readable PDF or DOCX within the limits
     */
    CvImportResponseDto readFile(MultipartFile file);

    /**
     * The user's own public, non-fork repositories, most recently pushed first.
     *
     * @throws io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException when GitHub has no such user
     */
    List<GithubRepositoryResponseDto> listGithubRepositories(String username);
}
