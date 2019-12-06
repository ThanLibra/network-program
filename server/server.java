import java.io.BufferedReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import javafx.scene.shape.Path;
/*
 * TRAN KIM HIEU 
 * 17020750
 * 
 * server mo tai cong: 8080
 * server co the phuc vu nhieu client, su dung da luong
 * mo ta: server phuc vu cac yeu cau cho client: 
 * 			1- gui file cho client
 * 			2- nhan messages va hien thi message
 * 			3- nhan file upload tu client
 * 			4- gui danh sach file cho client
 * 
*/

public class server {
	static String pathF = ".\\SharedFolder";
//	static String author = "Tran Kim Hieu";
	static int countClient = 0;

	static synchronized void setCountThread() {
    	countClient  = ThreadRun.activeCount() - 1;
    };
    

	public static void main(String[] args) throws IOException {
		System.out.print("hello i'm server!\n");
		String port = "8080";
		// server is listening on port
		ServerSocket ss = new ServerSocket(8080);
		// client request
		while (true) {
			Socket s = null;

			try {
				// socket object to receive incoming client requests
				s = ss.accept();

				System.out.println("A new client is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				System.out.println("Assigning new thread for this client");

				// create a new thread object
				Thread t = new ThreadRun(s, dis, dos);

				// Invoking the start() method
				t.start();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}

class ThreadRun extends Thread {
	String pathF = ".\\SharedFolder";
	OutputStream wBinary = null;
	final DataInputStream is;
	final DataOutputStream os;
	final Socket sClient;
	final String shutDown = new String("@logout");
	final String downFile = new String("download");
	final String showFile = new String("@show");
	final String uploadFile = new String("upload");

	public ThreadRun(Socket sClient, DataInputStream is, DataOutputStream os) {
		this.is = is;
		this.os = os;
		this.sClient = sClient;
	}

	@Override
	public void run() {
		server.setCountThread();
		System.out.println("count thread ------- " + String.valueOf(server.countClient));
		FileInputStream inputStream = null;
		String mess = "";
		mess = mess.replaceAll("\\n", "");
		try {

			this.os.writeUTF("Create thread success\n");
			while (true) {
				try {
					// ---receive message
					ReceiveProtocol rec = new ReceiveProtocol(this.is, this.os);
					mess = rec.runStr();
					// ---end receive message
//					mess = this.is.readUTF();
					if (mess.isEmpty())
						return;
					System.out.println(String.valueOf(this.sClient.getPort()) + "said :" + mess + "\n");

					if (mess.equals(this.shutDown)) {
						this.is.close();
						this.os.close();
						this.sClient.close();
						System.out.println("close connecting to" + String.valueOf(this.sClient.getPort()));
						break;
					}
					if (mess.equals(this.showFile)) {
						// --list name
						File fileL = new File("SharedFolder");
						String[] fileList = fileL.list();
						String listName = "\n";
						int count = 1;
						for (String name : fileList) {
							listName = listName + String.valueOf(count) + " : " + name + "\n";
							count++;
						}
						// --end list name

						// ----send messages
						SendProtocol send = new SendProtocol(this.os, this.is);
						send.setStringProtocol("str", listName);
						send.run();
						// -----end send message

//						this.os.writeUTF(listName);
//						this.os.flush();
					}
					if (mess.contains(this.downFile)) {
						// ----list name
						File fileL = new File("SharedFolder");
						String[] fileList = fileL.list();
						String listName = "\n";
						int count = 1;
						for (String name : fileList) {
							listName = listName + String.valueOf(count) + " : " + name + "\n";
							count++;
						}
						// ----end list name

						String valueSub = null;
						valueSub = mess.substring(0, 8);
						System.out.println(valueSub);
						String endMes = mess.substring(9, mess.length());
						boolean hasFile = false;
						for (String name : fileList) {
							if (name.equals(endMes)) {
								hasFile = true;
							}
						}
						if (!hasFile) {
							System.out.println("Dont have file " + endMes + "\n");
							os.writeUTF("Don't have file");
							os.flush();
						} else {
							SendProtocol send = new SendProtocol(this.os, this.is);
							send.setFileProtocol("bys", pathF + "\\" + endMes, endMes, "");
							send.run();
						}
					}

					if (mess.contains(uploadFile)) {
						InputStream rBinary = null;
						String valueSub = mess.substring(0, 6);
						String endMes = mess.substring(7, mess.length());
						boolean hasFile = false;
						String lines;
						if (endMes.contains(":"))
							hasFile = true;
						if (!hasFile)
							System.out.println("Dont have file " + endMes + "\n");
						else {
							System.out.println("\noh yeah! start take file " + endMes + "...");
							// get size of file and file name
							String[] arrOfStr = endMes.split(":", 2);
							String fileName = arrOfStr[1];
							String sizeF = arrOfStr[0];
							System.out.println("size is: " + sizeF + "\n" + fileName + "\n");
							int fileSize = Integer.parseInt(sizeF);
							if (fileSize > 0) {
								os.writeUTF(sizeF);
								os.flush();
							}
							ReceiveProtocol recF = new ReceiveProtocol(this.is, fileName, fileSize, this.os);
							String status = recF.runBys();
						}

					}
				} catch (IOException e) {
					// e.printStackTrace();
					System.out.println("Client " + String.valueOf(this.sClient.getPort()) + " Error! Exit!");
					this.is.close();
					this.os.close();
					this.sClient.close();
					return;
				}
			}

		} catch (IOException e) {
//			e.printStackTrace();
			return;
		}
	}
}

class SendProtocol {

	final String _beforeStr = "s";
	final String _beforeBys = "b";
	final String typeStr = "str";
	final String typeBys = "bys";
	private DataOutputStream os = null;
	private DataInputStream is = null;
	private String protocol = "";
	private String dataStr = "";
	private String path = "";
	private String name = "";
	private String statement = "";

	public SendProtocol(DataOutputStream os, DataInputStream is) {
		this.os = os;
		this.is = is;
	}

	public void setStringProtocol(String protocol, String dataStr) {
		this.protocol = protocol;
		this.dataStr = dataStr;

	}

	public void setFileProtocol(String protocol, String path, String name, String statement) {
		this.protocol = protocol;
		this.path = path;
		this.name = name;
		this.statement = statement;
	}

	public void run() {
		if (this.protocol.equals(this.typeStr)) {
			try {
				this.os.writeChars(this._beforeStr);
				this.os.writeUTF(this.dataStr);
				this.os.flush();
			} catch (IOException e) {
				return;
			}
		}
		if (this.protocol.equals(this.typeBys)) {
			try {
				byte[] dataBys = new byte[1000];
				FileInputStream inputStream = new FileInputStream(this.path);
				File ff = new File(this.path);
				if (ff.exists()) {
					long sF = ff.length();
					if (this.statement.isEmpty()) {
						this.os.writeUTF(String.valueOf(sF) + ":" + this.name);
						System.out.println(String.valueOf(sF) + ":" + this.name);
						this.os.flush();
					} else {
						SendProtocol send = new SendProtocol(os, is);
						send.setStringProtocol("str", "upload" + " " + String.valueOf(sF) + ":" + this.name);
						send.run();
						System.out.println("upload" + " " + String.valueOf(sF) + ":" + this.name);
					}

				}

				int total = 0;
				int nRead = 0;
				String line = this.is.readUTF();
				if (line.equals(String.valueOf(ff.length()))) {
					while ((nRead = inputStream.read(dataBys)) != -1) {
						total += nRead;
						this.os.write(dataBys, 0, nRead);

					}
				}
				System.out.println("Read " + total + " bytes");
				line = this.is.readUTF();
			} catch (IOException e) {
				return;
			}
		}
	}
}

class ReceiveProtocol {

	final String _beforeStr = "s";
	final String _beforeBys = "b";
	private DataInputStream is = null;
	private DataOutputStream os = null;
	private String protocol = "";
	private String dataStr = "";
	private byte[] dataBys = new byte[1000];
	private String path = "";
	private int fileSize = 0;

	public ReceiveProtocol(DataInputStream is, String path, int fileSize, DataOutputStream os) {
		this.is = is;
		this.os = os;
		this.path = path;
		this.fileSize = fileSize;
	}

	public ReceiveProtocol(DataInputStream is, DataOutputStream os) {
		this.is = is;
		this.os = os;

	}

	public String runStr() {
		try {
			String protocol = "";
			char s = this.is.readChar();
			protocol = Character.toString(s);

			if (protocol.equals(this._beforeStr)) {
				String value = this.is.readUTF();
				return value;
			}
		} catch (IOException e) {
			return null;
		}
		return null;
	}

	public String runBys() {
		try {
			File f = new File(this.path);
			f.createNewFile();
			FileOutputStream wFile = new FileOutputStream(f);
			byte[] buffer = new byte[1000];
			int size;
			int total = 0;
			while ((size = this.is.read(buffer)) != -1) {
				total += size;
				if (total >= fileSize) {
					wFile.write(buffer, 0, size);
					wFile.close();
					break;
				} else {
					wFile.write(buffer, 0, size);
					wFile.flush();
				}

			}
			System.out.println("download success!\n");
			os.writeUTF("THANK YOU I HAVE FILE");
			os.flush();
			return "success";
		} catch (IOException e) {
			System.out.println("ex io file");
			return null;
		}
	}
}