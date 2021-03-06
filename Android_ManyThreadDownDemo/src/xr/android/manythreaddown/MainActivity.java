package xr.android.manythreaddown;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * @ClassName: MainActivity
 * @Description:多线程下载安卓客户端
 * @author iamxiarui@foxmail.com
 * @date 2016年4月7日 下午11:11:24
 * 
 */
public class MainActivity extends Activity implements OnClickListener {

	private Context thisContext = MainActivity.this;

	private Button downButton, pauseButton;
	private EditText threadCountText;
	private LinearLayout progressBarLayout;
	private ProgressBar progressBar;

	private String DOWN_PATH = "http://172.25.10.172:8080/Web_UploadExeDemo/testApp.exe";

	//每个线程下载文件的大小
	private int BLOCK_SIZE = 0;
	//正在运行的线程数量
	private int RUN_COUNT = 0;
	//线程数
	private int THREAD_COUNT = 0;
	
	//进度条的数据集合
	private Map <Integer,ProgressBar> map = new HashMap<Integer,ProgressBar> ();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		downButton = (Button) findViewById(R.id.downButton);
		pauseButton = (Button) findViewById(R.id.pauseButton);
		threadCountText = (EditText) findViewById(R.id.threadCountText);

		downButton.setOnClickListener(this);
		pauseButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {

		// 得到线程的数量
		String threadCount_str = threadCountText.getText().toString().trim();
		THREAD_COUNT = Integer.parseInt(threadCount_str);
		// 初始化进度条布局文件
		progressBarLayout = (LinearLayout) findViewById(R.id.progressBarLayout);

		switch (v.getId()) {
		case R.id.downButton:
			// 先清空已经存在的进度条
		    progressBarLayout.removeAllViews();
			for (int i = 0; i < THREAD_COUNT; i++) {
				// 依次将进度条加入布局中
				progressBar = (ProgressBar) View.inflate(thisContext, R.layout.progress_bar_layout, null);
				//将进度条加入集合中
				map.put(i, progressBar);
				progressBarLayout.addView(progressBar);
			}

			// 开启线程下载
			new Thread(new Runnable() {

				@Override
				public void run() {
					downFile();
				}
			}).start();

			break;
		case R.id.pauseButton:
			Toast.makeText(thisContext, "功能暂时无法使用", Toast.LENGTH_SHORT).show();
			break;
		default:
			break;
		}

	}

	/**
	* @Title: downFile
	* @Description:下载方法，主要功能是预留空间和分配线程下载大小
	* @param 
	* @return void
	* @throws
	*/
	public void downFile() {
		try {
			URL url = new URL(DOWN_PATH);
			HttpURLConnection openConnection = (HttpURLConnection) url.openConnection();
			openConnection.setRequestMethod("GET");
			openConnection.setConnectTimeout(5 * 1000);

			int responseCode = openConnection.getResponseCode();

			if (responseCode == 200) {
				// 得到文件的长度
				int fileLength = openConnection.getContentLength();
				RandomAccessFile randomAccessFile = new RandomAccessFile(new File(getFileName(DOWN_PATH)), "rw");
				// 给下载的文件预留空间
				randomAccessFile.setLength(fileLength);

				// 每个线程所需下载的大小为 文件大小 除以 线程数量
				BLOCK_SIZE = fileLength / THREAD_COUNT;

				// 循环开启线程
				for (int THREAD_ID = 0; THREAD_ID < THREAD_COUNT; THREAD_ID++) {
					// 线程下载起始位置
					int START_INDEX = THREAD_ID * BLOCK_SIZE;
					// 线程下载结束位置
					int END_INDEX = (THREAD_ID + 1) * BLOCK_SIZE - 1;

					// 如果是最后一个线程
					if (THREAD_ID == THREAD_COUNT - 1) {
						// 结束位置为文件大小-1
						END_INDEX = fileLength - 1;
					}

					// 开启线程下载
					new UploadThread(THREAD_ID, START_INDEX, END_INDEX).start();
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** 
	* @ClassName: UploadThread 
	* @Description:每个线程下载的方法
	* @author iamxiarui@foxmail.com
	* @date 2016年4月7日 下午11:13:38 
	*  
	*/ 
	public class UploadThread extends Thread {

		private int threadId;
		private int startIndex;
		private int endIndex;
		// 上次文件下载的位置
		private int lastPostion;
		
		//当前线程总共下载的数据进度
		private int currentThreadTotalProgress;

		public UploadThread(int threadId, int startIndex, int endIndex) {
			this.threadId = threadId;
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.currentThreadTotalProgress = endIndex -startIndex +1;
		}

		@Override
		public void run() {
			
			//获取当前线程对应ProgressBar
			ProgressBar progressBar = map.get(threadId);

			// 同步代码块 只要进入 线程数加一
			synchronized (UploadThread.class) {
				RUN_COUNT = RUN_COUNT + 1;
			}

			try {
				URL url = new URL(DOWN_PATH);

				HttpURLConnection openConnection = (HttpURLConnection) url.openConnection();
				openConnection.setRequestMethod("GET");
				openConnection.setConnectTimeout(5 * 1000);

				System.out.println("理论上下载：   线程： " + threadId + ",开始位置： " + startIndex + ",结束位置：" + endIndex);

				// 读取上一次下载结束的位置，如果存在则继续当前位置下载 不存在则从开始下载
				File fileLast = new File(getFilePath() + threadId + ".txt");
				if (fileLast.exists()) {
					BufferedReader bufferedReader = new BufferedReader(
							new InputStreamReader(new FileInputStream(fileLast)));
					// 读取文件上次下载位置
					String lastPostion_str = bufferedReader.readLine();
					lastPostion = Integer.parseInt(lastPostion_str);
					openConnection.setRequestProperty("Range", "bytes:" + lastPostion + "-" + endIndex);
					System.out.println("实际下载：  线程：" + threadId + "，开始位置：" + lastPostion + ";结束位置:" + endIndex);
					bufferedReader.close();
				} else {
					// 如果是首次下载 那么 上次下载位置 就是开始位置
					lastPostion = startIndex;
					openConnection.setRequestProperty("Range", "bytes:" + lastPostion + "-" + endIndex);
					System.out.println("实际下载：  线程：" + threadId + "，开始位置：" + lastPostion + ";结束位置:" + endIndex);
				}

				// 200 为全部资源请求信息 206 为部分资源请求信息
				if (openConnection.getResponseCode() == 206) {
					InputStream inputStream = openConnection.getInputStream();
					RandomAccessFile randomAccessFile = new RandomAccessFile(new File(getFileName(DOWN_PATH)), "rw");
					// 从文件的偏移量开始存储
					randomAccessFile.seek(lastPostion);

					byte[] buf = new byte[512];
					int length = -1;
					int totalFile = 0;
					while ((length = inputStream.read(buf)) != -1) {
						randomAccessFile.write(buf, 0, length);

						totalFile = totalFile + length;
						// 去保存当前线程下载的位置，保存到文件中
						int currentThreadPostion = lastPostion + totalFile;// 计算出当前线程本次下载的位置
						// 创建随机文件保存当前线程下载的位置
						File filePause = new File(getFilePath() + threadId + ".txt");
						RandomAccessFile accessfile = new RandomAccessFile(filePause, "rwd");
						accessfile.write(String.valueOf(currentThreadPostion).getBytes());
						accessfile.close();
						
						//计算线程下载的进度并设置进度
						int currentprogress = currentThreadPostion -startIndex;
						progressBar.setMax(currentThreadTotalProgress);//设置进度条的最大值
						progressBar.setProgress(currentprogress);//设置进度条当前进度
					}

					inputStream.close();
					randomAccessFile.close();

					System.out.println("线程： " + threadId + ",下载完毕");

					// 同步代码块 如果线程下载完毕 删除所有预下载文件
					synchronized (UploadThread.class) {
						RUN_COUNT = RUN_COUNT - 1;
						
						//下载完毕 更新UI
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(thisContext, "下载完毕", 0).show();
							}
						});
						
						if (RUN_COUNT == 0) {
							System.out.println("所有线程下载完成");
							for (int i = 0; i < THREAD_COUNT; i++) {
								File fileDelete = new File(getFilePath() + i + ".txt");
								fileDelete.delete();
							}
						}

					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			super.run();
		}

	}

	/**
	* @Title: getFileName
	* @Description:得到文件的路径名称
	* @param @param url
	* @param @return
	* @return String
	* @throws
	*/
	public String getFileName(String url) {

		//lastIndexOf : 返回指定子字符串在此字符串中最右边出现处的索引。
		//Environment.getExternalStorageDirectory() 拿到SD卡位置
		return Environment.getExternalStorageDirectory() + "/" + url.substring(url.lastIndexOf("/"));

	}

	/**
	* @Title: getFilePath
	* @Description:得到文件路径
	* @param @return
	* @return String
	* @throws
	*/
	public String getFilePath() {

		return Environment.getExternalStorageDirectory() + "/";
	}

}
