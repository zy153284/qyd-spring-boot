package com.qyd.content;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
    private final ContentService service;
    public ContentController(ContentService service){this.service=service;}

    @GetMapping("/public/content")
    List<ContentDto> published(@RequestParam(required=false) ContentType type){return service.published(type);}

    @GetMapping("/content")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    List<ContentDto> list(@RequestParam(required=false) ContentType type,@RequestParam(required=false) ContentStatus status){
        return service.list(type,status);
    }
    @GetMapping("/content/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    ContentDto get(@PathVariable String id){return service.get(id);}
    @PostMapping("/content")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    ContentDto create(@Valid @RequestBody ContentRequest request,Authentication auth){return service.create(request,auth.getName());}
    @PutMapping("/content/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    ContentDto update(@PathVariable String id,@Valid @RequestBody ContentRequest request){return service.update(id,request);}
    @PostMapping("/content/{id}/publish")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    ContentDto publish(@PathVariable String id){return service.transition(id,ContentStatus.PUBLISHED);}
    @PostMapping("/content/{id}/offline")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    ContentDto offline(@PathVariable String id){return service.transition(id,ContentStatus.OFFLINE);}
    @DeleteMapping("/content/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    void delete(@PathVariable String id){service.delete(id);}

    public record ContentRequest(@NotNull ContentType type,@NotBlank @Size(max=300)String title,
            @Size(max=500)String summary,@NotBlank String body,@Size(max=500)String imageUrl,
            @Size(max=500)String targetUrl){}
    public record ContentDto(String id,String type,String title,String summary,String body,String imageUrl,
            String targetUrl,String status,Instant publishedAt,Instant createdAt){
        static ContentDto of(ContentItem i){return new ContentDto(i.getId(),i.type.name(),i.title,i.summary,i.body,
                i.imageUrl,i.targetUrl,i.status.name(),i.publishedAt,i.getCreatedAt());}
    }
}

enum ContentType { NOTICE, NEWS, ADVERTISEMENT }
enum ContentStatus { DRAFT, PUBLISHED, OFFLINE }

@Service @Transactional
class ContentService {
    private final ContentRepository repository;
    ContentService(ContentRepository repository){this.repository=repository;}
    @Transactional(readOnly=true)
    List<ContentController.ContentDto> published(ContentType type){
        List<ContentItem> values=type==null?repository.findAllByStatusOrderByPublishedAtDesc(ContentStatus.PUBLISHED):
                repository.findAllByTypeAndStatusOrderByPublishedAtDesc(type,ContentStatus.PUBLISHED);
        return values.stream().map(ContentController.ContentDto::of).toList();
    }
    @Transactional(readOnly=true)
    List<ContentController.ContentDto> list(ContentType type,ContentStatus status){
        return repository.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream()
                .filter(x->type==null||x.type==type).filter(x->status==null||x.status==status)
                .map(ContentController.ContentDto::of).toList();
    }
    @Transactional(readOnly=true) ContentController.ContentDto get(String id){return ContentController.ContentDto.of(require(id));}
    ContentController.ContentDto create(ContentController.ContentRequest r,String actor){
        return ContentController.ContentDto.of(repository.save(new ContentItem(r,actor)));
    }
    ContentController.ContentDto update(String id,ContentController.ContentRequest r){
        ContentItem item=require(id);item.update(r);return ContentController.ContentDto.of(item);
    }
    ContentController.ContentDto transition(String id,ContentStatus target){
        ContentItem item=require(id);
        if(target==ContentStatus.PUBLISHED){item.status=target;item.publishedAt=Instant.now();}
        else if(target==ContentStatus.OFFLINE&&item.status==ContentStatus.PUBLISHED)item.status=target;
        else throw new ResponseStatusException(HttpStatus.CONFLICT,"invalid content transition");
        return ContentController.ContentDto.of(item);
    }
    void delete(String id){ContentItem item=require(id);if(item.status==ContentStatus.PUBLISHED)
        throw new ResponseStatusException(HttpStatus.CONFLICT,"published content must be offline first");repository.delete(item);}
    private ContentItem require(String id){return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"content not found"));}
}

@Entity @Table(name="content_item")
class ContentItem extends BaseEntity {
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)ContentType type;
    @Column(nullable=false,length=300)String title;@Column(length=500)String summary;@Column(nullable=false,columnDefinition="TEXT")String body;
    @Column(length=500)String imageUrl;@Column(length=500)String targetUrl;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)ContentStatus status;
    Instant publishedAt;@Column(nullable=false,length=36)String createdBy;
    protected ContentItem(){}
    ContentItem(ContentController.ContentRequest r,String actor){update(r);createdBy=actor;status=ContentStatus.DRAFT;}
    void update(ContentController.ContentRequest r){type=r.type();title=r.title();summary=r.summary();body=r.body();imageUrl=r.imageUrl();targetUrl=r.targetUrl();}
}
interface ContentRepository extends JpaRepository<ContentItem,String>{
    List<ContentItem>findAllByStatusOrderByPublishedAtDesc(ContentStatus status);
    List<ContentItem>findAllByTypeAndStatusOrderByPublishedAtDesc(ContentType type,ContentStatus status);
}
