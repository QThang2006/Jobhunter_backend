package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.SkillRepository;

import java.util.Optional;

@Service
public class SkillService {
    private final SkillRepository skillRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public SkillService(SkillRepository skillRepository, SimpMessagingTemplate simpMessagingTemplate) {
        this.skillRepository = skillRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public boolean isNameExist(String name){
        return skillRepository.existsByName(name);
    }

    public Skill createSkill(Skill s){
        this.simpMessagingTemplate.convertAndSend("/topic/skills", "REFRESH");
        return skillRepository.save(s);
    }

    public Skill fetchSkillById(long id){
        Optional<Skill> skillOptional = skillRepository.findById(id);
        if(skillOptional.isPresent()){
            return skillOptional.get();
        }
        return null;
    }

    public Skill updateSkill(Skill s){
        this.simpMessagingTemplate.convertAndSend("/topic/skills", "REFRESH");
        return skillRepository.save(s);
    }

    public ResultPaginationDTO fetchAllSkills(Specification<Skill> spec, Pageable pageable){
        Page<Skill> pageUser = skillRepository.findAll(spec,pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageUser.getNumber()+1);
        mt.setPageSize(pageUser.getSize());
        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageUser.getContent());
        return rs;
    }

    public void deleteSkill(long id){
        Optional<Skill> skillOptional = skillRepository.findById(id);
        Skill currentSkill = skillOptional.get();
        currentSkill.getJobs().forEach(job -> job.getSkills().remove(currentSkill));

        currentSkill.getSubscribers().forEach(subscriber -> subscriber.getSkills().remove(currentSkill));

        this.simpMessagingTemplate.convertAndSend("/topic/skills", "REFRESH");
        skillRepository.delete(currentSkill);
    }
}
