package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.training.dto.AdminTrainingResponseDto;
import com.optimisante.backend.domain.training.dto.ApplicationFeeRequestDto;
import com.optimisante.backend.domain.training.dto.TrainingRejectRequestDto;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import com.optimisante.backend.domain.training.service.AdminTrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.optimisante.backend.config.security.MobilityAdmin;

@RestController
@RequestMapping("/api/v1/admin/trainings")
@RequiredArgsConstructor
@MobilityAdmin
public class AdminTrainingResource {

    private final AdminTrainingService adminTrainingService;

    @GetMapping
    public ResponseEntity<List<AdminTrainingResponseDto>> listTrainings(
            @RequestParam(required = false) TrainingApprovalStatus status) {
        return ResponseEntity.ok(adminTrainingService.listTrainings(status));
    }

    /**
     * Fixe les frais de dossier de la formation.
     *
     * <p>Reserve a l'administration de la mobilite, et volontairement separe du formulaire du
     * partenaire : ces frais sont une <b>recette OptimiSante</b>. Les exposer dans la mise a
     * jour cote partenaire laisserait l'etablissement fixer la remuneration d'un travail qu'il
     * n'effectue pas.</p>
     *
     * <p>Contrairement a une modification par le partenaire, elle ne renvoie pas la formation
     * en attente de validation : le contenu pedagogique n'a pas change.</p>
     */
    @PatchMapping("/{id}/application-fee")
    public ResponseEntity<AdminTrainingResponseDto> setApplicationFee(
            @PathVariable UUID id, @Valid @RequestBody ApplicationFeeRequestDto dto) {
        return ResponseEntity.ok(adminTrainingService.setApplicationFee(id, dto.applicationFee()));
    }

    /**
     * Valide et publie une formation, en fixant ses frais de dossier dans le meme geste.
     *
     * <p>Le corps est <b>facultatif</b> : absent, les frais existants sont conserves. Present
     * avec {@code applicationFee} nul, il retire le tarif propre et fait revenir la formation
     * a la valeur globale. Confondre les deux ferait perdre un tarif deja saisi a chaque
     * revalidation.</p>
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<AdminTrainingResponseDto> approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApplicationFeeRequestDto dto) {
        return ResponseEntity.ok(
                adminTrainingService.approve(id, dto != null, dto == null ? null : dto.applicationFee()));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<AdminTrainingResponseDto> reject(
            @PathVariable UUID id, @Valid @RequestBody TrainingRejectRequestDto dto) {
        return ResponseEntity.ok(adminTrainingService.reject(id, dto.reason()));
    }
}
