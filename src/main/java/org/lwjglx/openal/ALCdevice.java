package org.lwjglx.openal;

import org.lwjgl.openal.ALCCapabilities;

import java.util.HashMap;

/**
 * Created by gudenau on 6/1/2017.
 * <p>
 * LWJGL3
 */
public class ALCdevice{
    final long device;
    final ALCCapabilities capabilities;
    private volatile boolean valid;
    private final HashMap<Long, ALCcontext> contexts = new HashMap<>();

    ALCdevice(long device){
        this.device = device;
        valid = true;
        capabilities = org.lwjgl.openal.ALC.createCapabilities(device);
    }

    public boolean equals(Object device){
        if(device instanceof ALCdevice){
            return ((ALCdevice)device).device == this.device;
        }
        return super.equals(device);
    }

    void addContext(ALCcontext context){
        synchronized(contexts){
            contexts.put(context.context, context);
        }
    }

    void removeContext(ALCcontext context){
        synchronized(contexts){
            contexts.remove(context.context);
        }
    }

    void setInvalid(){
        valid = false;
        synchronized(contexts){
            for(ALCcontext context : contexts.values()){
                context.setInvalid();
            }
            contexts.clear();
        }
    }

    public boolean isValid(){
        return valid;
    }
}
