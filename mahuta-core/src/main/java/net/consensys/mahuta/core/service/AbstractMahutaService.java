package net.consensys.mahuta.core.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.consensys.mahuta.core.domain.common.Content;
import net.consensys.mahuta.core.domain.common.Metadata;
import net.consensys.mahuta.core.domain.common.MetadataAndPayload;
import net.consensys.mahuta.core.domain.common.pagination.Page;
import net.consensys.mahuta.core.domain.common.pagination.PageRequest;
import net.consensys.mahuta.core.domain.common.query.Query;
import net.consensys.mahuta.core.domain.createindex.CreateIndexRequest;
import net.consensys.mahuta.core.domain.createindex.CreateIndexResponse;
import net.consensys.mahuta.core.domain.deindexing.DeindexingRequest;
import net.consensys.mahuta.core.domain.deindexing.DeindexingResponse;
import net.consensys.mahuta.core.domain.get.GetRequest;
import net.consensys.mahuta.core.domain.get.GetResponse;
import net.consensys.mahuta.core.domain.getindexes.GetIndexesRequest;
import net.consensys.mahuta.core.domain.getindexes.GetIndexesResponse;
import net.consensys.mahuta.core.domain.indexing.CIDIndexingRequest;
import net.consensys.mahuta.core.domain.indexing.IndexingRequest;
import net.consensys.mahuta.core.domain.indexing.IndexingResponse;
import net.consensys.mahuta.core.domain.indexing.InputStreamIndexingRequest;
import net.consensys.mahuta.core.domain.indexing.OnylStoreIndexingRequest;
import net.consensys.mahuta.core.domain.indexing.StringIndexingRequest;
import net.consensys.mahuta.core.domain.search.SearchRequest;
import net.consensys.mahuta.core.domain.search.SearchResponse;
import net.consensys.mahuta.core.domain.updatefield.UpdateFieldRequest;
import net.consensys.mahuta.core.domain.updatefield.UpdateFieldResponse;
import net.consensys.mahuta.core.exception.ValidationException;
import net.consensys.mahuta.core.service.indexing.IndexingService;
import net.consensys.mahuta.core.service.storage.StorageService;
import net.consensys.mahuta.core.utils.BytesUtils;
import net.consensys.mahuta.core.utils.ValidatorUtils;
import net.consensys.mahuta.core.utils.lamba.Throwing;

/**
 * 
 * 
 * @author gjeanmart<gregoire.jeanmart@gmail.com>
 *
 */
@Slf4j
public abstract class AbstractMahutaService implements MahutaService {
    protected static final String REQUEST = "request";
    
    protected final StorageService storageService;
    protected final IndexingService indexingService;
    protected final boolean noPin;

    protected AbstractMahutaService(StorageService storageService, IndexingService indexingService, boolean noPin) {
        ValidatorUtils.rejectIfNull("storageService", storageService, "Configure the storage service");
        ValidatorUtils.rejectIfNull("indexingService", indexingService, "Configure the indexer service");

        this.storageService = storageService;
        this.indexingService = indexingService;
        this.noPin = noPin;
    }

    @Override
    public CreateIndexResponse createIndex(CreateIndexRequest request) {
        ValidatorUtils.rejectIfNull(REQUEST, request);

        indexingService.createIndex(request.getName(), request.getConfiguration());

        return CreateIndexResponse.of();
    }

    @Override
    public GetIndexesResponse getIndexes(GetIndexesRequest request) {
        List<String> indexes = indexingService.getIndexes();

        return GetIndexesResponse.of().indexes(indexes);
    }

    @Override
    public IndexingResponse index(IndexingRequest request) {

        ValidatorUtils.rejectIfNull(REQUEST, request);

        // Write content
        byte[] content = null;
        String contentId = null;
        String contentType = request.getContentType();

        if (request instanceof InputStreamIndexingRequest) {
            InputStream contentIS = ((InputStreamIndexingRequest) request).getContent();
            content = BytesUtils.convertToByteArray(contentIS);
            contentId = storageService.write(content, noPin);
            contentType = Optional.ofNullable(contentType)
                    .orElseGet(Throwing.rethrowSupplier(() -> URLConnection.guessContentTypeFromStream(contentIS)));

        } else if (request instanceof CIDIndexingRequest) {
            String cid = ((CIDIndexingRequest) request).getCid();
            content = ((ByteArrayOutputStream) storageService.read(cid)).toByteArray();
            contentId = cid;

        } else if (request instanceof StringIndexingRequest) {
            String contentStr = ((StringIndexingRequest) request).getContent();
            content = contentStr.getBytes();
            contentId = storageService.write(contentStr.getBytes(), noPin);

        } else if (request instanceof OnylStoreIndexingRequest) {
            contentId = storageService.write(BytesUtils.convertToByteArray(((OnylStoreIndexingRequest) request).getContent()), false);
            
            return IndexingResponse.of(contentId);

        } else {
            throw new UnsupportedOperationException(request.getClass().getName() + " isn't supported yet");
        }

        // Index content
        String indexDocId;
        if(request.isIndexContent()) {
            indexDocId = indexingService.index(request.getIndexName(), request.getIndexDocId(), contentId, 
                    contentType, content, !noPin, request.getIndexFields());
        } else {
            indexDocId = indexingService.index(request.getIndexName(), request.getIndexDocId(), contentId, 
                    contentType, null, !noPin, request.getIndexFields());
        }
        

        // Pin replica
        if(!noPin) {
            Content contentToPin = Content.of(contentId);
            storageService.getReplicaSet().forEach(pinningService ->
                CompletableFuture.runAsync(() -> pinningService.pin(contentToPin.getContentId(), 
                        request.getIndexName() + "-" + indexDocId, 
                        request.getIndexFields()))
            );
        }

        // Result 
        return IndexingResponse.of(request.getIndexName(), indexDocId, contentId, contentType,
                !noPin, request.getIndexFields());
    }

    @Override
    public UpdateFieldResponse updateField(UpdateFieldRequest request) {
        ValidatorUtils.rejectIfNull(REQUEST, request);

        indexingService.updateField(
                request.getIndexName(), 
                request.getIndexDocId(), 
                request.getKey(), 
                request.getValue());

        return UpdateFieldResponse.of();
    }

    @Override
    public DeindexingResponse deindex(DeindexingRequest request) {

        ValidatorUtils.rejectIfNull(REQUEST, request);

        Metadata metadata = indexingService.getDocument(request.getIndexName(), request.getIndexDocId());

        indexingService.deindex(request.getIndexName(), request.getIndexDocId());

        storageService.getReplicaSet()
            .forEach(pinningService -> pinningService.unpin(metadata.getContentId()));

        return DeindexingResponse.of();
    }

    @Override
    public GetResponse get(GetRequest request) {

        ValidatorUtils.rejectIfNull(REQUEST, request);

        byte[] content = null;
        String contentId = null;
        Metadata metadata = null;
        
        // If an index is passed, we try to find some metadata, either by indexDocId or contentId
        if(!ValidatorUtils.isEmpty(request.getIndexName())) {
            
            if (!ValidatorUtils.isEmpty(request.getIndexDocId())) {
                metadata = indexingService.getDocument(request.getIndexName(), request.getIndexDocId());
                contentId = metadata.getContentId();
                content = metadata.getContent();

            } else if (!ValidatorUtils.isEmpty(request.getContentId())) {
                Query query = Query.newQuery().equals(IndexingService.HASH_INDEX_KEY, request.getContentId());
                Page<Metadata> result = indexingService.searchDocuments(request.getIndexName(), query,
                        PageRequest.singleElementPage());

                if (result.isEmpty()) {
                    log.warn("Document [hash: {}] not found in the index {}", request.getContentId(), request.getIndexName());
                    contentId = request.getContentId();
                } else {
                    metadata = result.getElements().get(0);
                    contentId = metadata.getContentId();
                    content = metadata.getContent();
                }
                
            } else {
                throw new ValidationException("request must contain 'indexDocId' or 'contentId'");
            }
            
        } else if(ValidatorUtils.isEmpty(request.getContentId())) {
            throw new ValidationException("request must contain 'contentId'");
        
        } else {
            contentId = request.getContentId();
        }
        

        // Payload
        OutputStream payload = null;
        if (request.isLoadFile() && content == null) {
            payload = storageService.read(contentId);
        } else if (request.isLoadFile() && content != null) {
            payload = BytesUtils.convertToOutputStream(content);
        }

        return GetResponse.of().metadata(metadata).payload(payload);
    }

    @Override
    public SearchResponse search(SearchRequest request) {

        ValidatorUtils.rejectIfNull(REQUEST, request);

        Page<Metadata> metadatas = indexingService.searchDocuments(request.getIndexName(), request.getQuery(),
                request.getPageRequest());

        List<MetadataAndPayload> elements = metadatas.getElements().stream().map(m -> {
            MetadataAndPayload mp = new MetadataAndPayload();
            mp.setMetadata(m);
            if (request.isLoadFile() && m.getContent() == null) {
                mp.setPayload(storageService.read(m.getContentId()));
            } else if (request.isLoadFile() && m.getContent() != null) {
                mp.setPayload(BytesUtils.convertToOutputStream(m.getContent()));
            }
            return mp;
        }).collect(Collectors.toList());

        return SearchResponse.of().result(Page.of(request.getPageRequest(), elements, metadatas.getTotalElements()));
    }

}
