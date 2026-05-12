package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.PermissionRepository;

import java.util.Optional;

@Service
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public PermissionService(PermissionRepository permissionRepository, SimpMessagingTemplate simpMessagingTemplate) {
        this.permissionRepository = permissionRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public boolean isPermissionExist(Permission p){
        return permissionRepository.existsByModuleAndApiPathAndMethod(p.getModule(), p.getApiPath(), p.getMethod());
    }

    public Permission create(Permission p){
        this.simpMessagingTemplate.convertAndSend("/topic/permissions", "REFRESH");
       return permissionRepository.save(p);
    }

    public Permission  fetchById(long id){
        Optional<Permission> permissionOptional = permissionRepository.findById(id);
        if(permissionOptional.isEmpty()){
            return null;
        }
        return permissionOptional.get();
    }

    public Permission update(Permission p){
        Permission permissionDB = fetchById(p.getId());
        if(permissionDB != null){
            permissionDB.setApiPath(p.getApiPath());
            permissionDB.setModule(p.getModule());
            permissionDB.setMethod(p.getMethod());
            permissionDB.setName(p.getName());

            this.simpMessagingTemplate.convertAndSend("/topic/permissions", "REFRESH");
            return permissionRepository.save(permissionDB);
        }
        return null;
    }

    public void delete(long id){
        Permission currentPermission = fetchById(id);
        currentPermission.getRoles().forEach(role -> role.getPermissions().remove(currentPermission));

        permissionRepository.delete(currentPermission);
        this.simpMessagingTemplate.convertAndSend("/topic/permissions", "REFRESH");
    }

    public ResultPaginationDTO fetchAllPermission(Specification<Permission> spec, Pageable pageable){
        Page<Permission> pagePermission = permissionRepository.findAll(spec,pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pagePermission.getNumber()+1);
        mt.setPageSize(pagePermission.getSize());
        mt.setPages(pagePermission.getTotalPages());
        mt.setTotal(pagePermission.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pagePermission.getContent());

        return  rs;
    }
}
