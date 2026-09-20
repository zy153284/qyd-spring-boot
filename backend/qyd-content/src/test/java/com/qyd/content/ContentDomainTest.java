package com.qyd.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentDomainTest {
    @Test void newContentAlwaysStartsAsDraft(){
        var request=new ContentController.ContentRequest(ContentType.NEWS,"运动资讯","摘要","正文",null,null);
        ContentItem item=new ContentItem(request,"user-1");
        assertEquals(ContentStatus.DRAFT,item.status);assertNull(item.publishedAt);
        assertEquals(ContentType.NEWS,item.type);assertEquals("user-1",item.createdBy);
    }
}
