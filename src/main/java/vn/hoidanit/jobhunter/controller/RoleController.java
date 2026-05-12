package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.RoleService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/roles")
    @ApiMessage("create a role")
    public ResponseEntity<Role> create(@Valid @RequestBody Role role) throws IdInvalidException {
        if(roleService.existByName(role.getName())) {
            throw new IdInvalidException("Role với name = "+ role.getName() +" đã tồn tại");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(role));
    }

    @PutMapping("/roles")
    @ApiMessage("update a role")
    public ResponseEntity<Role> update(@Valid @RequestBody Role role) throws IdInvalidException {
        if(roleService.fetchById(role.getId()) == null) {
            throw new IdInvalidException("Role với id = "+ role.getId() +" ko ton tai");
        }

//        if(roleService.existByName(role.getName())) {
//            throw new IdInvalidException("Role với name = "+ role.getName() +" đã tồn tại");
//        }

        return ResponseEntity.ok().body(roleService.update(role));
    }

    @DeleteMapping("/roles/{id}")
    @ApiMessage("delete a role")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        if(roleService.fetchById(id) == null){
            throw new IdInvalidException("Role với id = "+id+" ko ton tai");
        }
        roleService.delete(id);

        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/roles")
    @ApiMessage("get role with pagination")
    public ResponseEntity<ResultPaginationDTO> getAllPermission(
            @Filter Specification<Role> spec,
            Pageable pageable ) {

        return ResponseEntity.status(HttpStatus.OK).body(roleService.fetchAllRole(spec,pageable));
    }

    @GetMapping("/roles/{id}")
    @ApiMessage("fetch role by id")
    public ResponseEntity<Role> getById(@PathVariable("id") long id) throws IdInvalidException {
        Role role = roleService.fetchById(id);
        if(role == null){
            throw new IdInvalidException("Role với id = "+id+" ko ton tai");
        }

        return ResponseEntity.ok().body(role);
    }
}
