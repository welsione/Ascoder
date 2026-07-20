package cn.welsione.ascoder.repository;

/**
 * 无效仓库路径异常，当路径越界或不存在时抛出。
 */
public class InvalidRepositoryPathException extends RuntimeException {

    public InvalidRepositoryPathException(String message) {
        super(message);
    }
}
