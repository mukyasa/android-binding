package com.gueei.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import gueei.binding.AttributeBinder;
import gueei.binding.Binder;
import gueei.binding.BindingSyntaxResolver;
import gueei.binding.CollectionChangedEventArg;
import gueei.binding.CollectionObserver;
import gueei.binding.ConstantObservable;
import gueei.binding.IBindableView;
import gueei.binding.IObservable;
import gueei.binding.IObservableCollection;
import gueei.binding.InnerFieldObservable;
import gueei.binding.Observer;
import gueei.binding.ViewAttribute;
import gueei.binding.collections.ArrayListObservable;
import gueei.binding.converters.ROW_CHILD;
import gueei.binding.utility.WeakList;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class BindableTableLayout extends TableLayout implements IBindableView<BindableTableLayout> {
	private WeakList<Object> currentRowList = null;
	private CollectionObserver collectionObserver = null;
	
	private ArrayListObservable<Object> rowList = null;	
	private ROW_CHILD.RowChild rowChild = null;	
	
	private Observer observer = new Observer() {
		@Override
		public void onPropertyChanged(IObservable<?> prop, Collection<Object> initiators) {
			if( initiators == null || initiators.size() < 1)
				return;
			Object parent = initiators.toArray()[0];
			int pos = currentRowList.indexOf(parent);						
			ArrayList<Object> list = new ArrayList<Object>();
			list.add(parent);
			removeRows(list);						
			insertRow(pos, parent);	 
		}
	};
	
	private ObservableMultiplexer<Object> observableChildLayoutID = new ObservableMultiplexer<Object>(observer);
	private ObservableMultiplexer<Object> observableChildSpan = new ObservableMultiplexer<Object>(observer);	
	private ObservableCollectionMultiplexer<Object> observableCollectionRowChildren = new ObservableCollectionMultiplexer<Object>(observer);		
	
	public BindableTableLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BindableTableLayout(Context context) {
		super(context);
		init();
	}
	
	private void init() {
	}
	
	
	private void createItemSourceList(ArrayListObservable<Object> newRowList) {		
		if( rowList != null && collectionObserver != null)
			rowList.unsubscribe(collectionObserver);
				
		collectionObserver = null;
		rowList = newRowList;
		
		if(newRowList==null)
			return;

		currentRowList = null;	
		collectionObserver = new CollectionObserver() {			
			@SuppressWarnings("unchecked")
			@Override
			public void onCollectionChanged(IObservableCollection<?> collection, CollectionChangedEventArg args) {
				rowListChanged(args, (List<Object>)collection);
			}
		};
		
		rowList.subscribe(collectionObserver);
		newRowList(rowList);
	}	

	private void newRowList(List<Object> rows) {
		this.removeAllViews();	
		
		observableChildLayoutID.clear();
		observableChildSpan.clear();
		observableCollectionRowChildren.clear();		
				
		currentRowList = new WeakList<Object>();
		if( rows == null)
			return;	
		
		int pos=0;
		for(Object row : rows) {
			insertRow(pos, row);
			pos++;
		}
		
		currentRowList.addAll(rows);
	}
	
	private void rowListChanged(CollectionChangedEventArg e, List<Object> rows) {
		if( e == null || rows == null)
			return;
		
		int pos=-1;
		switch( e.getAction()) {
			case Add:
				pos = e.getNewStartingIndex();
				for(Object row : e.getNewItems()) {
					insertRow(pos, row);
					pos++;
				}
				break;
			case Remove:
				removeRows(e.getOldItems());	
				break;
			case Replace:
				removeRows(e.getOldItems());	
				pos = e.getNewStartingIndex();
				for(Object item : e.getNewItems()) {
					insertRow(pos, item);
					pos++;
				}
				break;
			case Reset:
				newRowList(rows);
				break;
			case Move:
				// currently the observable array list doesn't create this action
				throw new IllegalArgumentException("move not implemented");				
			default:
				throw new IllegalArgumentException("unknown action " + e.getAction().toString());
		}
		
		currentRowList = new WeakList<Object>(rows);
	}			

	private ViewAttribute<BindableTableLayout, Object> ItemSourceAttribute = 
			new ViewAttribute<BindableTableLayout, Object>(Object.class, BindableTableLayout.this, "ItemSource") {		
				@SuppressWarnings("unchecked")
				@Override
				protected void doSetAttributeValue(Object newValue) {
					if( !(newValue instanceof ArrayListObservable<?> ))
						return;
					rowList = (ArrayListObservable<Object>)newValue;
					
					if( rowChild != null )
						createItemSourceList(rowList);
				}

				@Override
				public Object get() {
					return rowList;
				}				
	};	
					
	private ViewAttribute<BindableTableLayout, Object> RowChildAttribute =
			new ViewAttribute<BindableTableLayout, Object>(Object.class, BindableTableLayout.this, "RowChild"){
				@Override
				protected void doSetAttributeValue(Object newValue) {	
					rowChild = null;
					if( newValue instanceof ROW_CHILD.RowChild ) {
						rowChild = (ROW_CHILD.RowChild) newValue;
						if( rowList != null )
							createItemSourceList(rowList);
					}
				}

				@Override
				public Object get() {
					return rowChild;
				}
	};				

	@Override
	public ViewAttribute<BindableTableLayout, ?> createViewAttribute(String attributeId) {	
		if (attributeId.equals("itemSource")) return ItemSourceAttribute;
		if (attributeId.equals("rowChild")) return RowChildAttribute;
		return null;
	}	
	
	private void insertRow(int pos, Object row) {		
		if( rowChild == null )
			return;
		
		IObservable<?> childDataSource = null;			
		InnerFieldObservable<?> ifo = new InnerFieldObservable<Object>(rowChild.childDataSource);
		if (ifo.createNodes(row)) {
			childDataSource = ifo;	
		} else {			
			Object rawField = BindingSyntaxResolver.getFieldForModel(rowChild.childDataSource, row);
			if (rawField instanceof IObservable<?>)
				childDataSource = (IObservable<?>)rawField;
		}				
		
		TableRow trow = createRow(childDataSource, pos, row);
				 											
		this.addView(trow,pos);		
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private TableRow createRow(IObservable<?> childDataSource, int pos, Object row) {
		TableRow tableRow = new TableRow(getContext());		
		InnerFieldObservable<?> ifo = null;
				
		if( childDataSource == null) {	
			TextView textView = new TextView(getContext());
			textView.setText("binding error - row: " + pos + " has no child datasource - please check binding:itemPath or the layout id in viewmodel");
			textView.setTextColor(Color.RED);
			tableRow.addView(textView);
		} else {			
			Object dataSource = childDataSource.get();	
								
			if( dataSource instanceof ArrayListObservable<?>) {
				ArrayListObservable<?> childItems = (ArrayListObservable<?>)dataSource;
				
				// we might have to change the current row if the child datasource changes
				observableCollectionRowChildren.add(childItems,row);
				
				for(int col=0; col < childItems.size(); col ++ ){
					Object childItem = childItems.get(col);
					
					int layoutId = rowChild.staticLayoutId;		
					if( layoutId < 1 && rowChild.layoutIdName != null ) {									
						IObservable<?> observable = null;
						ifo = new InnerFieldObservable<Object>(rowChild.layoutIdName);
						if (ifo.createNodes(childItem)) {
							observable = ifo;
						} else {			
							Object rawField = BindingSyntaxResolver.getFieldForModel(rowChild.layoutIdName, childItem);
							if (rawField instanceof IObservable<?>)
								observable = (IObservable<?>)rawField;
							else if (rawField!=null)
								observable= new ConstantObservable(rawField.getClass(), rawField);
						}
						
						if( observable != null) {											
							Object obj = observable.get();
							if(obj instanceof Integer) {
								observableChildLayoutID.add(observable, row);	
								layoutId = (Integer)obj;
							}
						}
					}
										
					View child = null;
					
					if( childItem != null ) {
						if( layoutId < 1 ) {
							TextView textView = new TextView(getContext());
							textView.setText("binding error - pos: " + pos + " has no layout - please check binding:itemPath or the layout id in viewmodel");
							textView.setTextColor(Color.RED);
							child = textView;
						} else {		
							Binder.InflateResult result = Binder.inflateView(getContext(), layoutId, tableRow, false);
							for(View view: result.processedViews){
								AttributeBinder.getInstance().bindView(getContext(), view, childItem);
							}
							child = result.rootView;						
						}						
					}
					
					TableRow.LayoutParams params = null;
					
					int colSpan = 1;
					
					// colspan
					if( rowChild.colspanName != null ) {									
						IObservable<?> observable = null;			
						ifo = new InnerFieldObservable(rowChild.colspanName);
						if (ifo.createNodes(childItem)) {
							observable = ifo;
						} else {			
							Object rawField = BindingSyntaxResolver.getFieldForModel(rowChild.colspanName, childItem);
							if (rawField instanceof IObservable<?>)
								observable = (IObservable<?>)rawField;
							else if (rawField!=null)
								observable= new ConstantObservable(rawField.getClass(), rawField);
						}
						
						if( observable != null) {											
							Object obj = observable.get();
							if(obj instanceof Integer) {
								observableChildSpan.add(observable, row);
								colSpan = (Integer)obj;																	
							}
						}
					}					
	
					if( tableRow.getLayoutParams() != null ) {
						params = new TableRow.LayoutParams(tableRow.getLayoutParams());
					}
					
					if( child != null ) {
						// ViewGroup.MarginLayoutParams ctor doesn't honor the margins
						// so this is a workaround - we have to use the child - not the row
						
						TableRow.LayoutParams rowParams =  (TableRow.LayoutParams)child.getLayoutParams();										
						ViewGroup.MarginLayoutParams margins = new ViewGroup.MarginLayoutParams(rowParams);
						margins.setMargins(rowParams.leftMargin, rowParams.topMargin, 
										   rowParams.rightMargin, rowParams.bottomMargin);

						params = new TableRow.LayoutParams(margins);
					}
														
					if( child != null ) {
						params.span = colSpan;
						params.column = col;
						tableRow.setLayoutParams(params);									
											
						tableRow.addView(child, params);
					}
				}				
			}						
		}

		return tableRow;
	}
	
	private void removeRows(List<?> deleteList) {
		if( deleteList == null || deleteList.size() == 0 || currentRowList == null)
			return;
		
		ArrayList<Object> currentPositionList = new ArrayList<Object>(Arrays.asList(currentRowList.toArray()));
		
		for(Object row : deleteList){
			int pos = currentPositionList.indexOf(row);
			observableChildLayoutID.removeParent(row);
			observableChildSpan.removeParent(row);
			observableCollectionRowChildren.removeParent(row);
			currentPositionList.remove(row);
			this.removeViewAt(pos);	
		}
	}

}
