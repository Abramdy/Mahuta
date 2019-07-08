package net.consensys.mahuta.api.http.test;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import io.ipfs.api.IPFS;
import net.consensys.mahuta.core.domain.indexing.IndexingRequest;
import net.consensys.mahuta.core.domain.indexing.IndexingResponse;
import net.consensys.mahuta.core.domain.indexing.StringIndexingRequest;
import net.consensys.mahuta.core.test.utils.ContainerUtils;
import net.consensys.mahuta.core.test.utils.IndexingRequestUtils;
import net.consensys.mahuta.core.test.utils.IndexingRequestUtils.BuilderAndResponse;
import net.consensys.mahuta.core.utils.BytesUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ReplicaTest extends WebTestReplicaUtils {
    
    @Configuration
    @EnableWebMvc
    @ComponentScan( basePackages = { "net.consensys.mahuta.api.http" } )
    static class ContextConfiguration { }
    

    private static IndexingRequestUtils indexingRequestUtils;
    
    @BeforeClass
    public static void init() throws IOException, InterruptedException {
        indexingRequestUtils = new IndexingRequestUtils(null, new IPFS(ContainerUtils.getHost("ipfs"), ContainerUtils.getPort("ipfs")), true);
    }
    
    @Test
    public void fetch() throws Exception {
        BuilderAndResponse<IndexingRequest, IndexingResponse> builderAndResponse = indexingRequestUtils.generateRandomStringIndexingRequest();
        StringIndexingRequest request = (StringIndexingRequest) builderAndResponse.getBuilder().getRequest();
        
        // Create Index 
        mockMvc.perform(post("/config/index/" + request.getIndexName())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BytesUtils.readFile("index_mapping.json")))
            .andExpect(status().isOk())
            .andDo(print());

        mockMvc.perform(post("/index").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        MvcResult response = mockMvc.perform(get("/query/fetch/"+builderAndResponse.getResponse().getContentId() + "?index="+builderAndResponse.getBuilder().getRequest().getIndexName()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(builderAndResponse.getBuilder().getRequest().getContentType()))
                .andReturn();
        
        assertEquals(request.getContent(), response.getResponse().getContentAsString());
    }
    
}
