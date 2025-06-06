package newgame;

public class MemuInstance {
    public final int index;
    public String name;
    public String deviceSerial;
    public String status;
    volatile String state = "Idle";
    private volatile boolean autoStartGameRunning = false;
    volatile boolean autoGatherRunning = false;

    public MemuInstance(int index, String name, String status, String deviceSerial) {
        this.index = index;
        this.name = (name != null && !name.isEmpty()) ? name : "Instance " + index;
        this.status = (status != null) ? status : "Unknown";
        this.deviceSerial = (deviceSerial != null) ? deviceSerial : "N/A";
    }

    public synchronized void setState(String state) {
        this.state = (state != null) ? state : "Idle";
    }

    public synchronized String getState() {
        return this.state;
    }

    public synchronized void setStatus(String status) {
        this.status = (status != null) ? status : "Unknown";
    }

    public synchronized String getStatus() {
        return this.status;
    }

    public synchronized boolean isAutoStartGameRunning() {
        return autoStartGameRunning;
    }

    public synchronized void setAutoStartGameRunning(boolean running) {
        this.autoStartGameRunning = running;
    }

    public synchronized boolean isAutoGatherRunning() {
        return autoGatherRunning;
    }

    public synchronized void setAutoGatherRunning(boolean running) {
        this.autoGatherRunning = running;
    }

    @Override
    public String toString() {
        return String.format("MemuInstance[%d: %s (%s)]", index, name, status);
    }
}