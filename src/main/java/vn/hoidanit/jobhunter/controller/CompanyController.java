package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping("/companies")
    public ResponseEntity<?> createCompany(@Valid @RequestBody Company company) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(this.companyService.handleCreateCompany(company));
    }

    @GetMapping("/companies")
    public ResponseEntity<ResultPaginationDTO> getCompany(@Filter Specification<Company> spec, Pageable pageable){
        return ResponseEntity.ok(companyService.handleGetCompany(spec,pageable));
    }

    @PutMapping("/companies")
    public ResponseEntity<Company> updateCompany(@Valid @RequestBody Company resCompany){
        Company updateCompanies = companyService.handleUpdateCompany(resCompany);
        return ResponseEntity.ok(updateCompanies);
    }

    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable("id") Long id){
        companyService.handleDeleteCompany(id);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/companies/{id}")
    @ApiMessage("fetch companies by id")
    public ResponseEntity<Company> fetchCompanyById(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Company> companyOptional = companyService.findById(id);
        if(companyOptional.isEmpty()){
            throw new IdInvalidException("Companies with id not exist");
        }

        return ResponseEntity.ok().body(companyOptional.get());
    }


}