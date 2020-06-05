import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

	static Random r = new Random();
	static InetAddress IPAddressClient;
	static int PortClient;

	static int seqNumServer = 9001;
	static int ackNumServer = 0;

	static double TIMEOUT = 2.5; // TIMEOUT DO CLIENTE (em segundos)

	public static void main(String[] args) throws Exception {

		final DatagramSocket socket = new DatagramSocket(9878);

		System.out.println("Aguardando conexao com o cliente..");

		// THREE WAY HANDSHAKE //
		boolean conectado = estabelecerConexao(socket);

		// TRANSMISSAO DE DADOS ///
		if (conectado) {
			transmitirDados(socket);
		}

		// ---------- TESTES ----------

		// finalizar(socket);

	}

	// THREE WAY HANDSHAKE SYN-ACK//
	public static boolean estabelecerConexao(DatagramSocket socket) throws Exception {
		boolean conectado = false;

		Segmento sSyn = receber(socket); // recebe o SYN do cliente

		if (sSyn.isSyn && !sSyn.isAck) {
			System.out.println("\nSYN => " + sSyn.toString());

			ackNumServer = sSyn.seqNum + 1;

			Segmento sAckSyn = new Segmento(seqNumServer, ackNumServer, true, true, false, false, 0); // envia SYN + ACK
			enviar(sAckSyn, socket, IPAddressClient);
			System.out.println("\nSYN + ACK Enviado!");

			System.out.println("\nSYN+ACK => " + sAckSyn.toString());
		}

		Segmento sAck = receber(socket); // recebe o ACK do cliente

		if (!sAck.isSyn && sAck.isAck) {
			System.out.println("\nACK => " + sAck.toString());
			seqNumServer = sAck.ackNum;

			System.out.println("\nConexao estabelecida! O cliente ja pode enviar os dados =)");
			conectado = true;

		}
		return conectado;
	}

	// TRANSMISSAO DE DADOS //
	public static void transmitirDados(DatagramSocket socket) throws Exception {
		boolean loop = true;

		while (loop) {

			Segmento recebido = receber(socket);

			// caso o servidor receba um segmento RST
			if (recebido.isRst) {
				loop = false;
				receberRST(socket, recebido); // recebe o segmento RST do cliente
                        } else if (recebido.isFin) {
                               loop = false;
                               finalizar(socket, recebido); // recebe o segmento FIN do cliente para finalizar a conexao
			} else {

				System.out.println("\nTamanho do Dado Recebido => " + recebido.dados);
				System.out.println("Header -> " + recebido.toString());

				int aleatorio = r.nextInt(10);
				if (aleatorio == 0) {
					aleatorio = 1;
				}

				int bytes = aleatorio * 100;

				seqNumServer += bytes;

				ackNumServer = recebido.seqNum + 1;

				Segmento enviado = new Segmento(seqNumServer, ackNumServer, false, false, false, false, bytes);
				Segmento backup = new Segmento(seqNumServer, ackNumServer, false, false, false, false, bytes);

				boolean enviou = enviar(enviado, socket, IPAddressClient);

				if (enviou) {
					System.out.println("\nO pacote foi enviado com sucesso! :)");
				} else {
					System.out.println("Retransmitindo...");
					boolean retransmitiu = retransmitir(backup, socket, backup, 5);

					if (retransmitiu) {
						System.out.println("O pacote foi enviado com sucesso! :)");

					} else {
						loop = false; // para o loop

						// comeca o RST
						enviarRST(socket);

						return;
					}
				}

				backup = new Segmento(seqNumServer, ackNumServer, false, false, false, false, bytes);
				System.out.println("\nTamanho do Dado Enviado: " + backup.dados);
				System.out.println("Header -> " + backup.toString());

			}
		}
	}

	public static boolean enviar(Segmento se, DatagramSocket socket, InetAddress IpAddress) throws IOException {
		boolean enviou;

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(64000); // buffer
		ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		os.flush(); // libera o buffer

		os.writeObject(se); // armazena o segmento
		os.flush(); // libera o buffer para o envio

		byte[] sendBuf = byteStream.toByteArray(); // buffer para enviar
		DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, IpAddress, PortClient); // datagrampacket ->
																									// pacote udp

		// para adiantar as coisas, os pacotes so ficarao corrompidos durante a
		// transmissao
		if (se.isAck || se.isSyn || se.isFin || se.isRst) {
			socket.send(packet);
			enviou = true;

		} else {
                        System.out.println("\nTIMEOUT atual: "+TIMEOUT);
                        
			System.out.println("\nEnviando => " + se.toString());
			int prob = r.nextInt(10); // probabilidade de 50% de corromper (1 a 5 nao corrompe, 5 por diante sim)
                        System.out.println("Numero aleatorio para corromper: " + prob);
                        
                        double time = r.nextDouble() * r.nextInt(20); // tempo 
                        System.out.println("Tempo de envio: "+time);
                        
                        // se for maior que o timeout
                         if (time > TIMEOUT) {
                            System.out.println("Tempo de envio ultrapassado.");
                            TIMEOUT = (TIMEOUT*2)+1;
                            System.out.println("\nTIMEOUT atual: "+TIMEOUT);
                            enviou = false;
                            return enviou; 
                        } else {
                        // altera varias coisas do cabecalho
			if (prob > 5) {
				int n = r.nextInt(100);
				se.ackNum = se.ackNum + n;

				n = r.nextInt(100);
				se.seqNum = se.seqNum + n;

				n = r.nextInt(100);
				se.dados = se.dados * n;

			} else {
				socket.send(packet);
				enviou = true;
				return enviou;
			}

			boolean integr = se.checkSum();

			// se nao estiver com integridade, retorna a confirmacao de envio como falso
			if (!integr) {
                                System.out.println("Pacote Corrompido detectado => " + se.toString());
				enviou = false;
			} else {
				socket.send(packet);
				enviou = true;
			}
                    }
		}

		os.close(); // fecha o stream de envio

		return enviou;
	}

	public static Segmento receber(DatagramSocket socket) {
		Segmento s = null;

		// buffer (array) de byte para receber dados
		byte[] recvBuf = new byte[64000];
		// pacote udp com o buffer e o tamanho do buffer
		DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);

		try {

			socket.receive(packet); // recebe o pacote

			IPAddressClient = packet.getAddress(); // recebe o ip do cliente para usar futuramente
			PortClient = packet.getPort(); // recebe a porta do cliente para usar futuramente

			System.out.println("\n-- Cliente " + IPAddressClient); // relatorio com as informacoes do cliente conectado
			System.out.println("Conectado na porta: " + PortClient);

			// array de bytes com stream
			ByteArrayInputStream byteStream = new ByteArrayInputStream(recvBuf);

			// stream para receber
			ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
			s = (Segmento) is.readObject(); // le e pega o objeto (funciona de forma similar ao metodo get)

			is.close(); // fecha o stream de entrada (receber)

		} catch (IOException ex) {
			System.out.println("Erro: " + ex.getMessage());
		} catch (ClassNotFoundException exc) {
			System.out.println("Erro: " + exc.getMessage());
		}

		return (s); // retorna o segmento recebido
	}

	// ====================== PROCESSO DE TROCA DE SEGMENTOS - FLAG FIN ======================
	public static void finalizar(DatagramSocket socket, Segmento sFin) throws Exception {
		System.out.println("\nIniciando a finalizacao da conexao...");
		 // recebe o segmento FIN do cliente

		if (sFin.isFin) {
			System.out.println("\nFIN => " + sFin.toString());

			// DEFINIR O seqNUM e o ackNUM DO ESQUEMA DO FIN
			seqNumServer = sFin.ackNum;
			ackNumServer = sFin.seqNum + 1;

			Segmento sAckFin = new Segmento(seqNumServer, ackNumServer, false, true, true, false, 0); // envia FIN + ACK

			if (sAckFin.isAck && sAckFin.isFin) {
				enviar(sAckFin, socket, IPAddressClient);
				System.out.println("\nFIN+ACK => " + sAckFin.toString());

				Segmento sAckF = receber(socket); // recebe o ACK do cliente

				if (!sAckF.isFin && sAckF.isAck) {
					System.out.println("\nACK => " + sAckF.toString());

					encerraConexao(sAckF, socket);
				}
			}

		}
	}

	/*
	 * METODO ENCERRAR CONEXAO
	 * 
	 * As flags FIN e RST usarao esse metodo Para FIN: Deve receber a flag FIN e o
	 * ACK ativos (true) Para RST: Deve receber a flag RST ativa.
	 */
	public static void encerraConexao(Segmento se, DatagramSocket socket) {
		System.out.println("\nEncerrando a conexao...");
		if (se.isAck) { // verificando se a flag ACK estÃ¡ ativa apÃ³s o recebimento do ACK + FIN
			socket.close();
			System.out.println("\nConexao encerrada. (FIN)");
		} else if (se.isRst) {
			socket.close();
			System.out.println("\nConexao encerrada. (RST)");
		} else {
			System.out.println("Houve um erro ao tentar encerrar a conexÃ£o.");
		}
	}

	// um mÃ©todo recursivo para a retransmissao
	public static boolean retransmitir(Segmento s, DatagramSocket socket, Segmento backup, int vezes) throws Exception {
		boolean retransmitiu = false;

		if (vezes == 0) { // caso exceda o numero maximo de tentativas (5)
			System.out.println("\nA retransmissao excedeu o numero de tentativas. Descartando pacote. . .");
			return retransmitiu;
		}

		System.out.println("Numero de tentativas restantes: " + vezes);
		boolean enviou = enviar(s, socket, IPAddressClient);

		if (!enviou) {
			s = new Segmento(seqNumServer, ackNumServer, backup.isSyn, backup.isAck, backup.isFin, backup.isRst,
					backup.dados); // backup para tratamento de um bug
			return retransmitir(s, socket, backup, vezes - 1); // diminui as vezes

		} else if (enviou) {
			System.out.println("\nRetransmissao concluida!");
			retransmitiu = true;
		}

		return retransmitiu;
	}

	// ------------- FLAG RST --------------

	// Server recebe RST do cliente e encerra imediatamente
	public static void receberRST(DatagramSocket socket, Segmento sRstClient) throws Exception {

		if (sRstClient.isRst) {
			System.out.println("\nRST do Cliente => " + sRstClient.toString());

			encerraConexao(sRstClient, socket); // encerra imediatamente apos receber do cliente

		} else {
			System.out.println("Algo errado... Segmento RST não foi recebido.");

		}

	}

	public static void enviarRST(DatagramSocket socket) throws Exception {
		seqNumServer += 1;
		// ackNumServer = 0;
		Segmento sRstServer = new Segmento(seqNumServer, ackNumServer, false, false, false, true, 0);

		if (sRstServer.isRst) {
			enviar(sRstServer, socket, IPAddressClient); // servidor envia segmento RST
			System.out.println("\nRST Enviado!");
			System.out.println("\nRST => " + sRstServer.toString());

			encerraConexao(sRstServer, socket); // encerra imediatamente
		}
        }
}
