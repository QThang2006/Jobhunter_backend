package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.PermissionRepository;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, UserRepository userRepository, SimpMessagingTemplate simpMessagingTemplate) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public boolean existByName(String name){
        return roleRepository.existsByName(name);
    }

    public Role create(Role role){
        if(role.getPermissions() != null){
            List<Long> reqPermission = role.getPermissions()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Permission> permissions = permissionRepository.findByIdIn(reqPermission);
            role.setPermissions(permissions);
        }
        this.simpMessagingTemplate.convertAndSend("/topic/roles", "REFRESH");
        return roleRepository.save(role);
    }

    public Role fetchById(long id){
        Optional<Role> roleOptional = roleRepository.findById(id);
        if(roleOptional.isEmpty()){
            return null;
        }
        return roleOptional.get();
    }

    public Role update(Role role){
        Role roleDB = fetchById(role.getId());

        if(role.getPermissions() != null){
            List<Long> reqPermission = role.getPermissions()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Permission> permissions = permissionRepository.findByIdIn(reqPermission);
            role.setPermissions(permissions);
        }

        roleDB.setName(role.getName());
        roleDB.setPermissions(role.getPermissions());
        roleDB.setActive(role.isActive());
        roleDB.setDescription(role.getDescription());
        roleDB = roleRepository.save(roleDB);
        this.simpMessagingTemplate.convertAndSend("/topic/roles", "REFRESH");

        return roleDB;
    }

    public void delete(long id){
        Role role = fetchById(id);
        if(role!= null){
            List<User> users = role.getUsers();
            users.forEach(item -> item.setRole(null));
            userRepository.saveAll(users);
        }
        roleRepository.deleteById(id);
        this.simpMessagingTemplate.convertAndSend("/topic/roles", "REFRESH");
    }

    public ResultPaginationDTO fetchAllRole(Specification<Role> spec, Pageable pageable){
        Page<Role> pageRole = roleRepository.findAll(spec,pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageRole.getNumber()+1);
        mt.setPageSize(pageRole.getSize());
        mt.setPages(pageRole.getTotalPages());
        mt.setTotal(pageRole.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageRole.getContent());

        return  rs;
    }
}
