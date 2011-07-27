package com.oldsch00l.BlueMouse;

import java.text.SimpleDateFormat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class BlueMouseSQLite extends SQLiteOpenHelper {

	public final static String NAME = "BlueMouse.db";
	public final static int VERSION = 1;
	public final static SimpleDateFormat SDateFormat = new SimpleDateFormat(
	"yyyy-MM-dd'T'HH:mm:ss'Z'");

	public final static String TABLE_DEVICES = "devices";

	private final static String CREATETABLE_DEVICES =
		"CREATE TABLE " + TABLE_DEVICES + " (" +
		"  address TEXT PRIMARY KEY," +
		"  name TEXT," +
		"  last_connect DATETIME);";

	public BlueMouseSQLite(Context context, String name, CursorFactory factory,
			int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATETABLE_DEVICES);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

}
