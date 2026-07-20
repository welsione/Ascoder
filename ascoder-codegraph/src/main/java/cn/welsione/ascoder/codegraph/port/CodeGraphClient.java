package cn.welsione.ascoder.codegraph.port;

import java.nio.file.Path;

/**
 * CodeGraph 客户端端口接口，定义代码图分析的所有操作。
 */
public interface CodeGraphClient {

    CodeGraphToolResult index(Path repositoryPath);

    default CodeGraphToolResult index(Path repositoryPath, Long projectSpaceId) {
        return index(repositoryPath);
    }

    default CodeGraphToolResult index(Path repositoryPath, Path codegraphIndexPath, Long projectSpaceId) {
        return index(repositoryPath, projectSpaceId);
    }

    CodeGraphToolResult sync(Path repositoryPath);

    default CodeGraphToolResult sync(Path repositoryPath, Long projectSpaceId) {
        return sync(repositoryPath);
    }

    boolean hasIndex(Path repositoryPath);

    CodeGraphToolResult context(Path repositoryPath, String question);

    CodeGraphToolResult query(Path repositoryPath, String search, Integer limit, String kind);

    CodeGraphToolResult files(Path repositoryPath, String filter, String pattern, String format, Integer maxDepth);

    CodeGraphToolResult callers(Path repositoryPath, String symbol, Integer limit);

    CodeGraphToolResult callees(Path repositoryPath, String symbol, Integer limit);

    CodeGraphToolResult impact(Path repositoryPath, String symbol, Integer depth);

    CodeGraphToolResult affected(Path repositoryPath, String files, Integer depth, String filter);

    /**
     * 区域探索，返回相关符号的源代码和调用路径（对应 codegraph explore CLI 命令）。
     */
    CodeGraphToolResult explore(Path repositoryPath, String query, Integer maxFiles);

    /**
     * 单符号详情，返回源代码 + 调用者/被调用者轨迹（对应 codegraph node CLI 命令）。
     */
    CodeGraphToolResult node(Path repositoryPath, String name, String file, Integer offset, Integer limit);
}
