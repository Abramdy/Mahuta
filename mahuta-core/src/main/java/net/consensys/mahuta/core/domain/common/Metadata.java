package net.consensys.mahuta.core.domain.common;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class Metadata {

    protected String indexName;
    protected String indexDocId;
    protected String contentId;
    protected String contentType;
    protected byte[] content;
    protected Map<String, Object> indexFields;

    public static Metadata of(String indexName, String indexDocId, String contentId, String contentType,
            byte[] content, Map<String, Object> indexFields) {
        return new Metadata(indexName, indexDocId, contentId, contentType, content, indexFields);
    }
}
