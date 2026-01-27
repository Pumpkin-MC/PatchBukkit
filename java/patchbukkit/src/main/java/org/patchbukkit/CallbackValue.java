package org.patchbukkit;

public class CallbackValue {

    public String callbackName;
    public Object[] args;

    public CallbackValue(String callbackName, Object[] args) {
        this.callbackName = callbackName;
        this.args = args;
    }

    public Object getArg(int arg) {
        return args[arg];
    }

    public Object argLength() {
        return args.length;
    }

    public String getCallbackName() {
        return callbackName;
    }
}
