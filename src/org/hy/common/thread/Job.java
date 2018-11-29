package org.hy.common.thread;

import org.hy.common.xml.XJava;

import java.util.ArrayList;
import java.util.List;

import org.hy.common.Busway;
import org.hy.common.Date;
import org.hy.common.Execute;
import org.hy.common.Help;
import org.hy.common.StringHelp;





/**
 * 任务配置信息
 * 
 * @author      ZhengWei(HY)
 * @createDate  2013-12-16
 * @version     v1.0  
 *              v2.0  2014-07-21：融合XJava、任务池、线程池的功能
 *              v3.0  2015-11-03：是否在初始时(即添加到Jobs时)，就执行一次任务
 *              v4.0  2016-07-08：支持轮询间隔：秒
 *              v5.0  2017-07-03：添加：1. 最后一次执行时间的记录。
 *                                     2. toString()方法中显示出下次的执行时间
 *              v5.1  2018-04-11  添加：执行次数的统计属性
 *              v5.2  2018-05-22  添加：执行历史日志
 *              v5.3  2018-08-21  修改：将xjavaID改为xid与XSQLNode统一，同时防止与接口 org.hy.common.XJavaID 中的方法冲突。
 *              v6.0  2018-11-29  添加：开始时间组，即开始时间可以有多个。
 *                                     可实现一项任务在多个时间点上周期执行，并且只须配置一个Job，而非多个Job。
 *                                     注：此功能对 "间隔类型:秒、分" 是无效的（只取最小时间为开始时间）
 *                                     建议人：邹德福、张德宏
 */
public class Job extends Task<Object> implements Comparable<Job>
{
    /** 间隔类型: 秒 */
    public  static final int  $IntervalType_Second = -2;
    
    /** 间隔类型: 分钟 */
    public  static final int  $IntervalType_Minute = 60;
    
    /** 间隔类型: 小时 */
    public  static final int  $IntervalType_Hour   = 60 * $IntervalType_Minute;
    
    /** 间隔类型: 天 */
    public  static final int  $IntervalType_Day    = 24 * $IntervalType_Hour;
    
    /** 间隔类型: 周 */
    public  static final int  $IntervalType_Week   = 7  * $IntervalType_Day;
    
    /** 间隔类型: 月 */
    public  static final int  $IntervalType_Month  = 1;
    
    /** 间隔类型: 手工执行 */
    public  static final int  $IntervalType_Manual = -1;
    
    private static       long $SerialNo            = 0;
    
    
    
    /** 任务编号 */
    private String         code;
    
    /** 任务配置名称 */
    private String         name;
    
    /** 
     * 运行的线程数
     * taskCount=1表示单例，否则时间点一到，无论上次执行的任务是否完成，都将运行一个新的任务。
     * 此属性要与this.code配合使用，this.code做为惟一标记
     */
    private int            taskCount;
    
    /** 间隔类型 */
    private int            intervalType;
    
    /** 间隔长度 */
    private int            intervalLen;
    
    /** 开始时间组。多个开始时间用分号分隔。多个开始时间对 "间隔类型:秒、分" 是无效的（只取最小时间为开始时间） */
    private List<Date>     startTimes;
    
    /** 下次时间 */
    private Date           nextTime;
    
    /** 下次时间组。 */
    private List<Date>     nextTimes;
    
    /** 最后一次的执行时间 */
    private Date           lastTime;
    
    /** XJava对象标识 */
    private String         xid;
    
    /** 执行的方法名 */
    private String         methodName;
    
    /** 描述 */
    private String         desc;
    
    /** 是否在初始时(即添加到Jobs时)，就执行一次任务（默认：不执行） */
    private boolean        isInitExecute;
    
    /** 当 isInitExecute = true 时，这个任务是串行立刻执行? 还是多线程池执行（默认：延时执行） */
    private boolean        isAtOnceExecute;
    
    private Jobs           jobs;
    
    /** 执行次数 */
    private long           runCount;
    
    /** 执行日志。记录最后32次内的执行时间 */
    private Busway<String> runLogs;
    
    
    
    private synchronized long GetSerialNo()
    {
        return ++$SerialNo;
    }
    
    
    
    public Job()
    {
        super("$JOB$");
        
        this.startTimes      = new ArrayList<Date>();
        this.startTimes.add(Date.getNowTime().getNextHour().getFirstTimeOfHour());
        this.nextTime        = null;
        this.nextTimes       = null;
        this.intervalType    = $IntervalType_Manual;
        this.intervalLen     = 1; 
        this.taskCount       = 1;
        this.isInitExecute   = false;
        this.isAtOnceExecute = false;
        this.lastTime        = null;
        this.runCount        = 0;
        this.runLogs         = new Busway<String>(32);
    }
    
    
    
    /**
     * 获取任务描述
     * 
     * @return
     */
    public String getTaskDesc()
    {
        return Help.NVL(this.getDesc() ,Help.NVL(this.getName() ,this.getCode()));
    }
    
    
    
    /**
     * 执行任务的方法
     */
    public void execute()
    {
        if ( Help.isNull(this.xid) )
        {
            throw new NullPointerException("Job.getXid() is null.");
        }
        
        if ( Help.isNull(this.methodName) )
        {
            throw new NullPointerException("Job.getMethodName() is null."); 
        }
        
        Object v_Object = XJava.getObject(this.xid.trim());
        if ( v_Object == null )
        {
            throw new NullPointerException("Job.getXid() = " + this.xid + " XJava.getObject(...) is null.");
        }
        
        try
        {
            this.lastTime = new Date();
            this.runCount++;
            this.runLogs.put(this.lastTime.getFullMilli());
            (new Execute(v_Object ,this.methodName.trim())).start();
        }
        catch (Exception exce)
        {
            exce.printStackTrace();
        }
        
        // 注意：delMonitor()方法不要加同步锁。否则会出现线程阻塞
        this.jobs.delMonitor(this);
        this.finishTask();
    }
    
    
    
    /**
     * 获取任务编号。
     * 
     * 因为每个任务对象都应当有独立的编号顺序。
     * 即每个Task实现类，实例化的第一个类的编号应当都从0开始编号，所以这个工作就由实现者来完成。
     * 
     * @return
     */
    public long getSerialNo()
    {
        return GetSerialNo();
    }
    
    
    
    /**
     * 获取下一次运行时间
     * 
     * @return
     */
    public Date getNextTime()
    {
        return this.getNextTime(Date.getNowTime());
    }
    
    
    
    /**
     * 获取下一次运行时间
     * 
     * @return
     */
    public Date getNextTime(final Date i_Now)
    {
        if ( this.intervalType == $IntervalType_Manual )
        {
            return new Date("9999-12-31 23:59:59");
        }
        
        if ( this.nextTime == null )
        {
            // 重新创建时间对象，防止nextTime修改影响this.startTime
            // startTimes已按从小到大排序过，此处取最小时间
            this.nextTime  = new Date(this.startTimes.get(0));
            this.nextTimes = new ArrayList<Date>();
            for (Date v_STime : this.startTimes)
            {
                this.nextTimes.add(new Date(v_STime));
            }
        }
        
        if ( this.intervalType == $IntervalType_Second )
        {
            if ( i_Now.equalsYMDHMS(this.nextTime) )
            {
                // Nothing.
            }
            else if ( i_Now.getTime() > this.nextTime.getTime() )
            {
                // 为什么减1秒呢？ 原因是Jobs中已等待间隔是1秒。
                this.nextTime.setTime(i_Now.getTime() + ((this.intervalLen - 1) * 1000));
            }
        }
        else if ( i_Now.equalsYMDHM(this.nextTime) )
        {
            // Nothing.
        }
        else if ( i_Now.getTime() > this.nextTime.getTime() )
        {
            // 间隔类型: 分钟 小时 天 周
            if ( this.intervalType >= $IntervalType_Minute )
            {
                // 为了性能，所以在if分支语句中写for
                for (Date v_NextTime : this.nextTimes)
                {
                    if ( i_Now.getTime() <= v_NextTime.getTime() )
                    {
                        continue;
                    }
                    
                    long v_DiffSec = (i_Now.getTime() - v_NextTime.getTime()) / 1000;
                    long v_PerC    = this.intervalType;
                    long v_Value   = ((int)(v_DiffSec / v_PerC)) * v_PerC;
                    
                    if ( v_Value < v_PerC * this.intervalLen )
                    {
                        v_Value = v_PerC * this.intervalLen;
                    }
                    else if ( v_Value == v_PerC * this.intervalLen )
                    {
                        // Nothing.
                    }
                    else if ( v_Value % (v_PerC * this.intervalLen) == 0 )
                    {
                        v_Value += v_PerC * this.intervalLen;
                    }
                    
                    v_Value = ((int)(v_Value / (v_PerC * this.intervalLen))) * v_PerC * this.intervalLen * 1000;
                    v_NextTime.setTime(v_NextTime.getTime() + v_Value);
                }
            }
            // 间隔类型: 月
            else
            {
                // 为了性能，所以在if分支语句中写for
                for (Date v_NextTime : this.nextTimes)
                {
                    if ( i_Now.getTime() <= v_NextTime.getTime() )
                    {
                        continue;
                    }
                    
                    while ( i_Now.getTime() >= v_NextTime.getTime() )
                    {
                        v_NextTime.setDate(v_NextTime.getNextMonth());
                    }
                    
                    // 计算间隔
                    for (int i=1; i<this.intervalLen; i++)
                    {
                        v_NextTime.setDate(v_NextTime.getNextMonth());
                    }
                }
            }
            
            Help.toSort(this.nextTimes);
            this.nextTime = this.nextTimes.get(0);
        }
        
        return this.nextTime;
    }
    
    
    /**
     * 获取：任务编号
     */
    public String getCode()
    {
        return code;
    }

    
    /**
     * 设置：任务编号
     * 
     * @param code 
     */
    public void setCode(String code)
    {
        this.code = code;
    }



    /**
     * 获取：间隔类型
     */
    public int getIntervalType()
    {
        return intervalType;
    }
    
    
    /**
     * 设置：间隔类型
     * 
     * @param intervalType 
     */
    public void setIntervalType(int intervalType)
    {
        this.nextTime     = null;
        this.nextTimes    = null;
        this.intervalType = intervalType;
    }
    
    
    /**
     * 间隔长度
     *
     * @return
     */
    public int getIntervalLen()
    {
        return intervalLen;
    }


    /**
     * 设置：间隔长度
     * 
     * @param i_IntervalLen
     */
    public void setIntervalLen(int i_IntervalLen)
    {
        if ( i_IntervalLen >= 1 )
        {
            this.intervalLen = i_IntervalLen;
        }
    }

    
    /**
     * 获取：运行的线程数
     * taskCount=1表示单例，否则时间点一到，无论上次执行的任务是否完成，都将运行一个新的任务。
     * 此属性要与this.code配合使用，this.code做为惟一标记
     */
    public int getTaskCount()
    {
        return taskCount;
    }

    
    /**
     * 设置：运行的线程数
     * taskCount=1表示单例，否则时间点一到，无论上次执行的任务是否完成，都将运行一个新的任务。
     * 此属性要与this.code配合使用，this.code做为惟一标记
     * 
     * @param taskCount 
     */
    public void setTaskCount(int taskCount)
    {
        this.taskCount = taskCount;
    }


    /**
     * 获取：任务配置名称
     */
    public String getName()
    {
        return name;
    }

    
    /**
     * 设置：任务配置名称
     * 
     * @param name 
     */
    public void setName(String name)
    {
        this.name = name;
    }


    /**
     * 获取：开始时间组。多个开始时间用分号分隔。多个开始时间对 "间隔类型:秒、分" 是无效的（只取最小时间为开始时间）
     * 
     * @return
     */
    public List<Date> getStartTimes()
    {
        return this.startTimes;
    }

    
    /**
     * 设置：开始时间组。多个开始时间用分号分隔。多个开始时间对 "间隔类型:秒、分" 是无效的（只取最小时间为开始时间）
     * 
     * @param i_StartTimesStr
     */
    public void setStartTime(String i_StartTimesStr)
    {
        if ( Help.isNull(i_StartTimesStr) )
        {
            return;
        }
        
        this.startTimes = new ArrayList<Date>();
        String [] v_STimeArr = StringHelp.replaceAll(i_StartTimesStr ,new String[]{"\t" ,"\n" ,"\r"} ,new String[]{""}).split(",");
        for (String v_STime : v_STimeArr)
        {
            this.startTimes.add(new Date(v_STime.trim()));
        }
        
        Help.toSort(this.startTimes);
    }


    /**
     * 获取：描述
     */
    public String getDesc()
    {
        return Help.NVL(this.desc);
    }

    
    /**
     * 设置：描述
     * 
     * @param xid 
     */
    public void setDesc(String i_Desc)
    {
        this.desc = i_Desc;
    }
    
    
    /**
     * 获取：XJava对象标识
     */
    public String getXid()
    {
        return xid;
    }

    
    /**
     * 设置：XJava对象标识
     * 
     * @param xid 
     */
    public void setXid(String xid)
    {
        this.xid = xid;
    }


    /**
     * 获取：执行的方法名
     */
    public String getMethodName()
    {
        return methodName;
    }

    
    /**
     * 设置：执行的方法名
     * 
     * @param methodName 
     */
    public void setMethodName(String methodName)
    {
        this.methodName = methodName;
    }


    public void setMyJobs(Jobs jobs)
    {
        this.jobs = jobs;
    }
    
    
    
    /**
     * 获取：是否在初始时(即添加到Jobs时)，就执行一次任务（默认：不执行）
     */
    public boolean isInitExecute()
    {
        return isInitExecute;
    }

    
    /**
     * 设置：是否在初始时(即添加到Jobs时)，就执行一次任务（默认：不执行）
     * 
     * @param isInitExecute 
     */
    public void setInitExecute(boolean isInitExecute)
    {
        this.isInitExecute = isInitExecute;
    }

    
    /**
     * 获取：当 isInitExecute = true 时，这个任务是串行立刻执行? 还是多线程池执行（默认：延时执行）
     */
    public boolean isAtOnceExecute()
    {
        return isAtOnceExecute;
    }

    
    /**
     * 设置：当 isInitExecute = true 时，这个任务是串行立刻执行? 还是多线程池执行（默认：延时执行）
     * 
     * @param isAtOnceExecute 
     */
    public void setAtOnceExecute(boolean isAtOnceExecute)
    {
        this.isAtOnceExecute = isAtOnceExecute;
    }

    
    /**
     * 获取：最后一次的执行时间
     */
    public Date getLastTime()
    {
        return lastTime;
    }

    
    /**
     * 设置：最后一次的执行时间
     * 
     * @param lastTime 
     */
    public void setLastTime(Date lastTime)
    {
        this.lastTime = lastTime;
    }
    

    
    /**
     * 获取：执行次数
     */
    public long getRunCount()
    {
        return runCount;
    }
    

    
    /**
     * 设置：执行次数
     * 
     * @param runCount 
     */
    public void setRunCount(long runCount)
    {
        this.runCount = runCount;
    }
    

    
    /**
     * 获取：执行日志。记录最后32次内的执行时间
     */
    public Busway<String> getRunLogs()
    {
        return runLogs;
    }
    

    
    /**
     * 设置：执行日志。记录最后32次内的执行时间
     * 
     * @param runLogs 
     */
    public void setRunLogs(Busway<String> runLogs)
    {
        this.runLogs = runLogs;
    }
    


    public int compareTo(Job i_Other)
    {
        if ( i_Other == null )
        {
            return 1;
        }
        else
        {
            int v_Ret = this.getNextTime().compareTo(i_Other.getNextTime());
            
            if ( v_Ret == 0 )
            {
                return this.getCode().compareTo(i_Other.getCode());
            }
            else
            {
                return v_Ret;
            }
        }
    }
    
    
    public String toString()
    {
        if ( this.nextTime == null )
        {
            return this.getTaskDesc();
        }
        else
        {
            return this.nextTime.getFull() + " " + this.getTaskDesc();
        }
    }

}

