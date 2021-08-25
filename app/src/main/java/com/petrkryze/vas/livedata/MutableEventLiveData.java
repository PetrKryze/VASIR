package com.petrkryze.vas.livedata;

public class MutableEventLiveData<T> extends EventLiveData<T> {

    @Override
    public void postValue(T value) {
        super.postValue(value);
    }

    @Override
    public void setValue(T value) {
        super.setValue(value);
    }
}

