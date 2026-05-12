package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository, SimpMessagingTemplate simpMessagingTemplate) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;

        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public Company handleCreateCompany(Company c) {
        this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
        return this.companyRepository.save(c);
    }

    public ResultPaginationDTO handleGetCompany(Specification<Company> spec, Pageable pageable){
        Page<Company> pageUser = companyRepository.findAll(spec,pageable);
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

    public Company handleUpdateCompany(Company resCompany){
        Optional<Company> companyOptional = companyRepository.findById(resCompany.getId());
        if(companyOptional.isPresent()){
            Company res = companyOptional.get();
            res.setLogo(resCompany.getLogo());
            res.setName(resCompany.getName());
            res.setDescription(resCompany.getDescription());
            res.setAddress(resCompany.getAddress());
            this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
            return companyRepository.save(res);
        }
        return null;
    }

    public void handleDeleteCompany(Long id){
        Optional<Company> companyOptional = companyRepository.findById(id);
        if(companyOptional.isPresent()){
            Company com =companyOptional.get();
            List<User> users = userRepository.findByCompany(com);
            users.forEach(item -> item.setCompany(null));
            userRepository.saveAll(users);
        }
        this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
        companyRepository.deleteById(id);
    }

    public Optional<Company> findById(long id){
        return companyRepository.findById(id);
    }
}