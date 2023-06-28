/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.manager;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.diasemi.bleremote.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheManager {
    private static final String TAG = CacheManager.class.getSimpleName();

    // This class is used in order to save and restore the log output.
    // The original code read and wrote the file on every log entry,
    // which slowed down log refresh execution (on the main thread!).
    // A cache was added to retrieve the list quickly and the file is not written every time.
    private static HashMap<String, List> mCachedLists = new HashMap<>();
    private static final int WRITE_LIST_DELAY = 20000;
    private static long mLastWriteList;
    private static Handler mWriteListHandler = new Handler();

    public static boolean hasCached(final Context context, final String filename) {
        File file = new File(context.getExternalCacheDir(), filename);
        return file.exists();
    }

    @SuppressWarnings({
            "rawtypes", "resource"
    })
    public static List readList(final Context context, final String key) {
        List cachedList = mCachedLists.get(key);
        if (cachedList != null)
            return cachedList;
        File file = new File(context.getExternalCacheDir(), key);
        if (!file.exists())
            return null;
        Object object = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(file);
            ois = new ObjectInputStream(fis);
            object = ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "read()", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
        }
        if (object != null)
            mCachedLists.put(key, (List) object);
        return (ArrayList) object;
    }

    @SuppressWarnings({
            "rawtypes", "resource"
    })
    public static Map readMap(final Context context, final String key) {
        File file = new File(context.getExternalCacheDir(), key);
        Object object = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(file);
            ois = new ObjectInputStream(fis);
            object = ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "read()", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
        }
        return (Map) object;
    }

    @SuppressWarnings("resource")
    public static Object readObject(final Context context, final String key) {
        File file = new File(context.getExternalCacheDir(), key);
        Object object = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(file);
            ois = new ObjectInputStream(fis);
            object = ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "read()", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
        }
        return object;
    }

    public static boolean writeList(final Context context, final String key, final Object object, final boolean overwrite) {
        mWriteListHandler.removeCallbacksAndMessages(null);
        if (new Date().getTime() - mLastWriteList > WRITE_LIST_DELAY) {
            return doWriteList(context, key, object, overwrite);
        }
        else {
            mWriteListHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doWriteList(context, key, object, overwrite);
                }
            }, WRITE_LIST_DELAY);
        }
        return true;
    }

    @SuppressWarnings("resource")
    private static boolean doWriteList(final Context context, final String key, final Object object,
            final boolean overwrite) {
        boolean result = true;
        File file = new File(context.getExternalCacheDir(), key);
        if (overwrite) {
            file.delete();
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            if (object != null) {
                oos.writeObject(object);
            } else {
                result = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "write()", e);
            result = false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
        }
        mLastWriteList = new Date().getTime();
        return result;
    }

    @SuppressWarnings("resource")
    public static boolean writeMap(final Context context, final String key, final Object object,
            final boolean overwrite) {
        boolean result = true;
        File file = new File(context.getExternalCacheDir(), key);
        file.setReadable(true);
        file.setWritable(true);

        if (overwrite) {
            file.delete();
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            if (object != null) {
                oos.writeObject(object);
            } else {
                result = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "write()", e);
            result = false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("resource")
    public static boolean writeObject(final Context context, final String key, final Object object,
            final boolean overwrite) {
        boolean result = true;
        File file = new File(context.getExternalCacheDir(), key);
        if (overwrite) {
            file.delete();
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            if (object != null) {
                oos.writeObject(object);
            } else {
                result = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "write()", e);
            result = false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static boolean deleteString(final Context context, final String tag,
            final String listing) {
        List<String> list = readList(context, tag);
        if (list == null)
            return false;
        for (String listing2 : list) {
            if (listing2.equals(listing)) {
                boolean removed = list.remove(listing2);
                if (removed) {
                    writeList(context, tag, list, true);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void updateString(final Context context, final String tag, final String listing) {
        List<String> list = readList(context, tag);
        if (list == null)
            return;
        for (String listing2 : list) {
            if (listing2.equals(listing)) {
                int index = list.indexOf(listing2);
                list.set(index, listing);
                break;
            }
        }
        writeList(context, tag, list, true);
    }

    @SuppressWarnings("unchecked")
    public static void addString(final Context context, final String tag, final String message) {
        List<String> list = readList(context, tag);
        if (list == null) {
            list = new ArrayList<>();
        }
        try {
            list.add(message);
            writeList(context, tag, list, true);
        } catch (Exception e) {
            Log.e(TAG, "addString()", e);
        }
    }

    @SuppressWarnings("resource")
    public static String readString(final Context context, final String key) {
        File file = new File(context.getExternalCacheDir(), key);
        Object object = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(file);
            ois = new ObjectInputStream(fis);
            object = ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "read()", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (Exception e) {
                    Log.e(TAG, "read()", e);
                }
            }
        }
        return (String) object;
    }

    @SuppressWarnings("resource")
    public static boolean writeString(final Context context, final String key, final Object object,
            final boolean overwrite) {
        boolean result = true;
        File file = new File(context.getExternalCacheDir(), key);
        if (overwrite) {
            file.delete();
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            if (object != null) {
                oos.writeObject(object);
            } else {
                result = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "write()", e);
            result = false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {
                    Log.e(TAG, "write()", e);
                    result = false;
                }
            }
        }
        return result;
    }

    public static boolean hasExpired(final Context context, final String cacheName,
            final int expiryTime) {
        File file = new File(context.getExternalCacheDir(), cacheName);
        return (System.currentTimeMillis() - file.lastModified()) > (expiryTime * 60 * 1000);
    }

    @SuppressWarnings("unchecked")
    public static void clearCaches(final Context context) {
        File file = new File(context.getExternalCacheDir(), Constants.CACHED_MESSAGES);
        file.delete();
        mCachedLists.clear();
        mLastWriteList = 0;
    }
}