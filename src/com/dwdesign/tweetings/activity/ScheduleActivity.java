package com.dwdesign.tweetings.activity;

import java.util.Calendar;

import com.dwdesign.menubar.MenuBar;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.TextView;
import android.widget.Toast;

public class ScheduleActivity extends BaseActivity implements Constants {
	
	private TextView tvDisplayDate;
	private DatePicker dpResult;
	private TimePicker tpResult;
	
	private int year;
	private int month;
	private int day;
	private int hour;
	private int minute;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.schedule_tweet);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		/*Bundle bundle = getIntent().getExtras();
		String passedDatetime = null;
		if (bundle != null) {
			passedDatetime = bundle.getString(INTENT_KEY_SCHEDULE_DATE_TIME);
		}
		
		if (passedDatetime != null) {
			System.out.println(passedDatetime);
			String[] dateTimeParts = passedDatetime.split(" ");
			String[] dateParts = dateTimeParts[0].split("-");
			
			final String strYear = dateParts[0];
			final String strMonth = dateParts[1];
			final String strDay = dateParts[2];
			
			String[] timeParts = dateTimeParts[1].split(":");
			
			final String strHour = timeParts[0];
			final String strMins = timeParts[1];
			
			year = Integer.getInteger(strYear);
			month = Integer.getInteger(strMonth);
			day = Integer.getInteger(strDay);
			
			hour = Integer.getInteger(strHour);
			minute = Integer.getInteger(strMins);
		}*/
		
		setCurrentDateOnView();
		setCurrentTimeOnView();
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_schedule_actionbar, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				onBackPressed();
				break;
			}
			case MENU_SAVE: {
				Intent intent = new Intent();
				final Bundle bundle = new Bundle();
				bundle.putString(INTENT_KEY_SCHEDULE_DATE_TIME, new StringBuilder().append(year)
						   .append("-").append(month + 1).append("-").append(day)
						   .append(" ").append(hour).append(":").append(minute).toString());
				intent.putExtras(bundle);
				setResult(RESULT_OK, intent);
				finish();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onBackPressed() {
		setResult(RESULT_CANCELED, new Intent());
		finish();
	}
	
	// display current date
	public void setCurrentDateOnView() {
	 
			dpResult = (DatePicker) findViewById(R.id.dpResult);
	 
			if (year <= 0) {
				final Calendar c = Calendar.getInstance();
				year = c.get(Calendar.YEAR);
				month = c.get(Calendar.MONTH);
				day = c.get(Calendar.DAY_OF_MONTH);
			}		 
			// set current date into datepicker
			dpResult.init(year, month, day, mDatePickerListener);
			//dpResult.setMinDate(c.getTimeInMillis());
			if (android.os.Build.VERSION.SDK_INT >= 11) {
				configureDate();
			}
			
		}
	
	@TargetApi(11)
	public void configureDate() {
		dpResult.setCalendarViewShown(false);
	}
	
	// display current time
	public void setCurrentTimeOnView() {
	 
			tpResult = (TimePicker) findViewById(R.id.tpResult);
	 
			if (hour <= 0) {
				final Calendar c = Calendar.getInstance();
				hour = c.get(Calendar.HOUR_OF_DAY);
				minute = c.get(Calendar.MINUTE);
			}
			
			if (minute%TIME_PICKER_INTERVAL != 0){
	            int minuteFloor=minute-(minute%TIME_PICKER_INTERVAL);
	            minute=minuteFloor + (minute==minuteFloor+1 ? TIME_PICKER_INTERVAL : 0);
	            if (minute==60)
	                minute=0;
	            mIgnoreEvent=true;
	            tpResult.setCurrentMinute(minute);
	            mIgnoreEvent=false;
	        }
	 
			// set current time into timepicker
			tpResult.setCurrentHour(hour);
			tpResult.setCurrentMinute(minute);
			tpResult.setIs24HourView(false);
			
			tpResult.setOnTimeChangedListener(mTimePickerListener);
	 
	}
		
	private static final int TIME_PICKER_INTERVAL = 15;
	private boolean mIgnoreEvent = false;

	private TimePicker.OnTimeChangedListener mTimePickerListener=new TimePicker.OnTimeChangedListener(){
	    public void onTimeChanged(TimePicker timePicker, int hourOfDay, int aminute){
	        if (mIgnoreEvent)
	            return;
	        if (aminute%TIME_PICKER_INTERVAL!=0){
	            int minuteFloor= aminute-(aminute%TIME_PICKER_INTERVAL);
	            aminute=minuteFloor + (aminute==minuteFloor+1 ? TIME_PICKER_INTERVAL : 0);
	            if (aminute==60)
	                aminute=0;
	            mIgnoreEvent=true;
	            timePicker.setCurrentMinute(aminute);
	            mIgnoreEvent=false;
	        }
	        hour = hourOfDay;
            minute = aminute;

	    }
	};
	
	private DatePicker.OnDateChangedListener mDatePickerListener = new DatePicker.OnDateChangedListener() {
		
		@Override
		public void onDateChanged(DatePicker view, int ayear, int monthOfYear,
				int dayOfMonth) {
			year = ayear;
			month = monthOfYear;
			day = dayOfMonth;
			
		}
	};

}
