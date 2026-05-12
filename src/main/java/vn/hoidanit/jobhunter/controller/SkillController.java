package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.SkillService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class SkillController {
    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping("/skills")
    @ApiMessage("Create a skill")
    public ResponseEntity<Skill> create(@Valid @RequestBody Skill s) throws IdInvalidException {
        if(s.getName() != null && skillService.isNameExist(s.getName())){
            throw new IdInvalidException("Skill name = " + s.getName() + "đã tồn tại");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(s));
    }

    @PutMapping("/skills")
    @ApiMessage("Update a skill")
    public ResponseEntity<Skill> update(@Valid @RequestBody Skill s) throws IdInvalidException {
        Skill currentSkill = skillService.fetchSkillById(s.getId());
        if(currentSkill == null){
            throw new IdInvalidException("skill id = " + s.getId() + "ko tồn tại");
        }

        if(s.getName() != null && skillService.isNameExist(s.getName())) {
            throw new IdInvalidException("Skill name = " + s.getName() + "đã tồn tại");
        }
        currentSkill.setName(s.getName());
        return ResponseEntity.ok().body(skillService.updateSkill(currentSkill));
    }

    @GetMapping("/skills")
    @ApiMessage("fetch all skills")
    public ResponseEntity<ResultPaginationDTO> getAll(
            @Filter Specification<Skill> spec,
            Pageable pageable ) {

        return ResponseEntity.status(HttpStatus.OK).body(skillService.fetchAllSkills(spec,pageable));
    }

    @DeleteMapping("/skills/{id}")
    @ApiMessage("Delete a skill")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        Skill currentSkill = skillService.fetchSkillById(id);
        if(currentSkill == null){
            throw new IdInvalidException("skill id = " + id + "ko tồn tại");
        }
        skillService.deleteSkill(id);
        return ResponseEntity.ok().body(null);
    }

}
