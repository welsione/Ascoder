package cn.welsione.ascoder.repository.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import cn.welsione.ascoder.repository.CodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BranchWorkspaceSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesRepositorySummaryWithoutNestedRepository() throws Exception {
        CodeRepository repository = new CodeRepository();
        repository.setId(1L);
        repository.setName("demo");

        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setId(10L);
        workspace.setRepository(repository);
        workspace.setBranchName("main");
        workspace.setCommitSha("abc123");
        workspace.setWorktreePath("/tmp/demo/main");
        workspace.setCodegraphIndexPath("/tmp/codegraph/demo/main");
        workspace.setStatus(BranchWorkspaceStatus.READY);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(workspace));

        assertThat(json.has("repository")).isTrue();
        assertThat(json.get("repository").isTextual()).isTrue();
        assertThat(json.get("repositoryId").asLong()).isEqualTo(1L);
        assertThat(json.get("repository").asText()).isEqualTo("demo");
        assertThat(json.get("branch").asText()).isEqualTo("main");
    }
}
