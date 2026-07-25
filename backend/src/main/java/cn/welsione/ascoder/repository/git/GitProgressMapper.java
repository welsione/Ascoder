package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.task.TaskProgress;

/**
 * 将 git 命令的分阶段进度输出映射到任务整体进度的指定区间。
 *
 * <p>git clone/fetch 的进度按阶段独立计数（Counting -> Compressing -> Receiving -> Resolving），
 * 每个阶段从 0% 到 100%。本类按各阶段在整体操作中的权重，将分阶段百分比映射为连续递增的任务进度。</p>
 *
 * <p>阶段权重：</p>
 * <ul>
 *   <li>Counting objects: 0%~5%（远端枚举，快速）</li>
 *   <li>Compressing objects: 5%~10%（远端压缩）</li>
 *   <li>Receiving/Unpacking objects: 10%~85%（接收对象，主要耗时）</li>
 *   <li>Resolving deltas: 85%~95%（解决差异）</li>
 * </ul>
 */
public class GitProgressMapper {

    private final TaskProgress progress;
    private final int basePercent;
    private final int rangePercent;
    private int lastPercent;

    public GitProgressMapper(TaskProgress progress, int basePercent, int rangePercent) {
        this.progress = progress;
        this.basePercent = basePercent;
        this.rangePercent = rangePercent;
    }

    /**
     * 处理一行 git 输出，解析进度并更新 TaskProgress。
     *
     * @param line git 输出行
     */
    public void onLine(String line) {
        int gitPercent = GitProgressExtractor.extractPercent(line);
        if (gitPercent < 0) {
            return;
        }

        double stageStart;
        double stageEnd;
        if (line.contains("Counting")) {
            stageStart = 0.0;
            stageEnd = 0.05;
        } else if (line.contains("Compressing")) {
            stageStart = 0.05;
            stageEnd = 0.10;
        } else if (line.contains("Receiving") || line.contains("Unpacking")) {
            stageStart = 0.10;
            stageEnd = 0.85;
        } else if (line.contains("Resolving")) {
            stageStart = 0.85;
            stageEnd = 0.95;
        } else {
            return;
        }

        double stageProgress = stageStart + (stageEnd - stageStart) * gitPercent / 100.0;
        int taskPercent = basePercent + (int) (stageProgress * rangePercent);

        // 确保进度单调递增，不倒退
        if (taskPercent > lastPercent) {
            lastPercent = taskPercent;
            String message = line.replaceAll("\r", "").trim();
            if (message.length() > 100) {
                message = message.substring(0, 100) + "...";
            }
            progress.update(taskPercent, message);
        }
    }
}
