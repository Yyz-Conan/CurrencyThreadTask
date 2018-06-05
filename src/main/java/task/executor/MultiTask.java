package task.executor;


import java.util.ArrayList;
import java.util.List;

/**
 * MultiTask 多线程并发处理任务类
 *
 * @author yyz
 * @date 2017/7/20.
 * Created by prolog on 2017/7/20.
 */

public abstract class MultiTask<T> {
    private List<StackTraceElement> lockList = null;
    private List<Task> taskList = null;
    private int token = 0;
    /***生产标记*/
    private long productionMark = 0;
    /***消费标记*/
    private long consumerMark = 0;

    /**
     * 多线程并发处理任务
     *
     * @param count 开启线程的数量
     */
    public MultiTask(int count) {
        taskList = new ArrayList<>(count);
        lockList = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Task task = new Task(index);
            taskList.add(task);
            task.getExecutor().startTask();
        }
    }

    /**
     * 设置线数据程缓冲区大小
     *
     * @param count 缓冲区大小
     */
    public void setCacheMaxCount(int count) {
        for (Task task : taskList) {
            task.getExecutor().setCacheMaxCount(count);
        }
    }

    /**
     * 存储要处理的数据
     *
     * @param data 数据
     */
    public void pushData(T data) {
        Task task = taskList.get(token++);
        TaskEntity entity = new TaskEntity(productionMark++, data);
        task.getExecutor().pushToCache(entity);
        if (token >= taskList.size()) {
            token = 0;
        }
    }

    /**
     * 获取锁
     *
     * @param task 当前任务线程
     *             //     * @param mark 当前任务的mark
     */
    protected synchronized void lock(Task task) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        StackTraceElement currentElement = elements[elements.length - 1];
        boolean isHas = false;
        for (StackTraceElement element : lockList) {
            if (currentElement.getClassName().equals(element.getClassName())
                    && currentElement.getMethodName().equals(element.getMethodName())
                    && currentElement.getLineNumber() == element.getLineNumber()) {
                isHas = true;
                break;
            }
        }
        if (isHas) {
            while (taskList.size() > 1 && isHas) {
                if (isHas = lockList.contains(elements[elements.length - 1])) {
                    task.getExecutor().waitTask(0);
                } else {
                    break;
                }
            }
        } else {
            lockList.add(elements[elements.length - 1]);
        }
    }

    /**
     * 释放锁 通知其它线程可以继续运行
     */
    protected synchronized void unLock() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        StackTraceElement currentElement = elements[elements.length - 1];
        int index = 0;
        for (StackTraceElement element : lockList) {
            if (currentElement.getClassName().equals(element.getClassName())
                    && currentElement.getMethodName().equals(element.getMethodName())
                    && currentElement.getLineNumber() == element.getLineNumber()) {
                break;
            }
            index++;
        }
        lockList.remove(index);
        for (Task tmp : taskList) {
            tmp.getExecutor().resumeTask();
        }
    }

    protected synchronized void nextTask() {
        consumerMark++;
    }

    protected void onInitTask() {
    }

    protected void onReleaseTask() {
    }

    /**
     * 处理数据
     *
     * @param task 当前任务线程
     * @param data 要处理的数据
     * @param mark 当前任务的mark
     */
    protected abstract void onExecTask(Task task, T data, long mark);

    /**
     * 任务线程
     */
    protected class Task extends BaseConsumerTask<TaskEntity> {
        protected int token = 0;
        private TaskContainer container;
        private ConsumerTaskExecutor<TaskEntity> executor;

        Task(int token) {
            container = new TaskContainer(this);
            executor = container.getTaskExecutor();
            this.token = token;
        }

        public ConsumerTaskExecutor<TaskEntity> getExecutor() {
            return executor;
        }

        public int getToken() {
            return token;
        }

        @Override
        protected void onInitTask() {
            MultiTask.this.onInitTask();
            executor.setIdleStateSleep(true);
        }

        @Override
        protected void onProcess() {
            TaskEntity entity = executor.popCacheData();
            if (entity != null) {
                onExecTask(this, entity.data, entity.mark);
            }
        }

        @Override
        protected void onDestroyTask() {
            MultiTask.this.onReleaseTask();
        }

    }

    /**
     * 任务Entity
     */
    private class TaskEntity {
        long mark = 0;
        T data = null;

        TaskEntity(long mark, T data) {
            this.mark = mark;
            this.data = data;
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        if (taskList != null) {
            for (Task task : taskList) {
                task.getExecutor().stopTask();
            }
            taskList.clear();
            taskList = null;
        }
    }
}
