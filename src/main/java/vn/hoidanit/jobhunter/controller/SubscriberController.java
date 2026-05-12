package vn.hoidanit.jobhunter.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.service.SubscriberService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class SubscriberController {
    private final SubscriberService subscriberService;

    public SubscriberController(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @PostMapping("/subscribers")
    @ApiMessage(("create a subscriber"))
    public ResponseEntity<Subscriber> create(@Valid @RequestBody Subscriber sub) throws IdInvalidException {
        boolean isEmailExits = subscriberService.isExistsByEmail(sub.getEmail());
        if(isEmailExits){
            throw new IdInvalidException("Email "+sub.getEmail()+" da ton tai");
        }
        return ResponseEntity.ok().body(subscriberService.create(sub));
    }

    @PutMapping("/subscribers")
    @ApiMessage("update a subscriber")
    public ResponseEntity<Subscriber> update( @RequestBody Subscriber subRequest) throws IdInvalidException {
        Subscriber subsDB = subscriberService.findById(subRequest.getId());
        if(subsDB == null){
            throw new IdInvalidException("Id " + subRequest.getId() + "ko ton tai");
        }

        return ResponseEntity.ok().body(subscriberService.update(subsDB,subRequest));
    }

    @PostMapping("/subscribers/skills")
    @ApiMessage(("get subscriber skill"))
    public ResponseEntity<Subscriber> getSubscribersSkill() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "" ;
        return ResponseEntity.ok().body(subscriberService.findByEmail(email));
    }



}
