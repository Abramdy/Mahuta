package net.consensys.mahuta.core.domain.indexing;

import java.util.Map;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.consensys.mahuta.core.domain.common.Metadata;
import net.consensys.mahuta.core.domain.Response;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class IndexingResponse extends Metadata implements Response {

    private @Getter ResponseStatus status;

    public static IndexingResponse of(String indexName, String indexDocId, String contentId, String contentType, boolean pinned,
            Map<String, Object> indexFields) {

        IndexingResponse response = new IndexingResponse(ResponseStatus.SUCCESS);
        response.setIndexName(indexName);
        response.setIndexDocId(indexDocId);
        response.setContentType(contentType);
        response.setContentId(contentId);
        response.setIndexFields(indexFields);
        response.setPinned(pinned);

        return response;
    }
    
    public static IndexingResponse of(String contentId) {

        IndexingResponse response = new IndexingResponse(ResponseStatus.SUCCESS);
        response.setContentId(contentId);

        return response;
    }
    
    
    
}
