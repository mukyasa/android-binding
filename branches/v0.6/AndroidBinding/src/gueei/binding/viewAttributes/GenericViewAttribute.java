package gueei.binding.viewAttributes;

import gueei.binding.ViewAttribute;

import java.lang.reflect.Method;

import android.view.View;


public class GenericViewAttribute<Tv extends View, T> extends ViewAttribute<Tv, T>{
	public Method getter;
	public Method setter;

	@SuppressWarnings("unchecked")
	public GenericViewAttribute(Class<T> type, Tv view, String attributeName, Method getter, Method setter) 
		throws Exception{
		super(type,view, attributeName);
		this.getter = getter;
		this.setter = setter;
		T value = (T)this.getter.invoke(view);
		this.set(value);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T get() {
		if(getView()==null) return null;
		T value;
		try {
			value = (T)this.getter.invoke(getView());
			return value;
		} catch (Exception e){
			return null;
		}
	}

	@Override
	protected void doSetAttributeValue(Object newValue) {
		if(getView()==null) return;
		try{
			this.setter.invoke(getView(), newValue);
		}catch(Exception e){
			
		}
	}
}