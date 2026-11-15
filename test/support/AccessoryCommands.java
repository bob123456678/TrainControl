package support;

import java.util.ArrayList;
import java.util.List;
import org.traincontrol.marklin.udp.CS2Message;

/** What accessory commands say, read off the messages handed to the control station (`setSentMessageObserver`). */
public final class AccessoryCommands
{
    private AccessoryCommands()
    {
    }

    /** The positions sent to one accessory in these messages, in order: 0 turned (red), 1 straight (green). */
    public static List<Integer> positionsTo(List<CS2Message> messages, int uid)
    {
        List<Integer> positions = new ArrayList<>();

        synchronized (messages)
        {
            for (CS2Message m : messages)
            {
                byte[] d = m.getData();

                if (m.getCommand() == CS2Message.CMD_ACC_SWITCH && d.length > 4
                    && CS2Message.mergeBytes(new byte[] {d[0], d[1], d[2], d[3]}) == uid)
                {
                    positions.add((int) d[4]);
                }
            }
        }

        return positions;
    }
}
