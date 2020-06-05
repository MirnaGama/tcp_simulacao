import java.io.*;
import java.net.*;
import java.util.*;

public class Cliente {

	static int seqNumClient = 5001;
	static int ackNumClient = 0;
	static double TIMEOUT = 1.5; // TIMEOUT DO SERVIDOR (em segundos)
	static int portSERVER = 9878;

	static Random r = new Random();

	public static void main(String[] args) throws Exception {

		final DatagramSocket socket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName("localhost");

		/// THREE WAY HANDSHAKE SYN-ACK ///
		boolean conectado = estabelecerConexao(socket, IPAddress);

		// ENVIO DE DADOS //
		if (conectado) {
			transmitirDados(socket, IPAddress);

		}

	}

	// THREE WAY HANDSHAKE SYN-ACK //
	public static boolean estabelecerConexao(DatagramSocket socket, InetAddress IPAddress) throws Exception {

		boolean conectado = false;

		Segmento sSyn = new Segmento(seqNumClient, ackNumClient, true, false, false, false, 0); // primeiro SYN

		if (sSyn.isSyn && !sSyn.isAck) {
			enviar(sSyn, socket, IPAddress);
			System.out.println("\nSYN Enviado!");
			System.out.println("\nSYN => " + sSyn.toString());

                        System.out.println("Aguardando resposta do servidor...");
                        
			Segmento sAckSyn = receber(socket); // recebe o SYN + ACK do server

			if (sAckSyn.isSyn && sAckSyn.isAck) {
				System.out.println("\nSYN+ACK => " + sAckSyn.toString());

				seqNumClient = sAckSyn.ackNum;
				ackNumClient = sAckSyn.seqNum + 1;

				Segmento sAck = new Segmento(seqNumClient, ackNumClient, false, true, false, false, 0); // envia Ack
				enviar(sAck, socket, IPAddress);

				if (!sAck.isSyn && sAck.isAck) {
					System.out.println("\nACK Enviado!");
					System.out.println("\nACK => " + sAck.toString());

					conectado = true;
				}
			}
		}

		return conectado;

	}

	public static void transmitirDados(DatagramSocket socket, InetAddress IPAddress) throws Exception {
		boolean loop = true;
		
		Scanner sc = new Scanner(System.in);

		while (loop) {

			System.out.println("\n==== Entre com algum dado: (Caso queira encerrar a conexao, digite FIN) ========");
			String entrada = sc.next(); // digitar qualquer coisa / espa�o = proxima etapa
			
                        // uma condicao de que caso a entrada seja "FIN" vai pro metodo
			// FIN e encerra o loop
			if(entrada.equalsIgnoreCase("FIN")) {
				loop = false;
				finalizar(socket, IPAddress);
			        return;
			} else {

				int aleatorio = r.nextInt(10);
				if (aleatorio == 0) {
					aleatorio = 1;
				}
	
				int bytes = aleatorio * 100;
	
				seqNumClient += bytes;
	
				Segmento dado = new Segmento(seqNumClient, ackNumClient, false, false, false, false, bytes);
				Segmento backup = new Segmento(seqNumClient, ackNumClient, false, false, false, false, bytes); // precisa de um backup pq de alguma forma sempre retornava o segmento corrompido
	
				// if de boolean aqui
				boolean enviou = enviar(dado, socket, IPAddress);
	
				if (enviou) {
					System.out.println("\nO pacote foi enviado com sucesso! :)");
				} else {
					System.out.println("Retransmitindo...");
					boolean retransmitiu = retransmitir(backup, socket, IPAddress, backup, 5); /* um parametro eh um segmento (que vai retornar corrompido nÃ£o sei porque) 
					e o outro eh um backup desse mesmo segmento */
	
					if (retransmitiu) {
						System.out.println("\nO pacote foi enviado com sucesso! :)");
	
						// caso a transmissao nao de certo
					} else {
						loop = false; // encerra o loop
	
						enviarRST(socket, IPAddress); // envia um segmento com flag RST
						return;
					}
				}
	
				backup = new Segmento(seqNumClient, ackNumClient, false, false, false, false, bytes); // refaz o backup ele voltou corrompido de novo :( )
				System.out.println("\nTamanho do Dado Enviado: " + backup.dados);
				System.out.println("Header -> " + backup.toString());
	
				Segmento recebido = receber(socket); // agora aguarda o servidor mandar dados
	
				// caso o cliente receber um segmento contendo flag RST
				if (recebido.isRst) {
	
					loop = false; // quebra o loop
	
					receberRST(socket, IPAddress, recebido);
					return;
	
				} else {
					System.out.println("\nTamanho do Dado Recebido => " + recebido.dados);
					System.out.println("Header -> " + recebido.toString());
	
					ackNumClient = recebido.seqNum + 1; // para usar em futuras comunicacoes (ex: FIN)
				}
			}
		}
	}

	public static boolean enviar(Segmento se, DatagramSocket socket, InetAddress IpAddress) throws Exception {
		boolean enviou;

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(64000); // buffer
		ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		os.flush(); // libera o buffer

		os.writeObject(se); // armazena o segmento
		os.flush(); // libera o buffer para o envio

		byte[] sendBuf = byteStream.toByteArray(); // buffer para enviar
		DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, IpAddress, portSERVER); // datagrampacket -> pacote udp

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
                                
                                os.close();
                                
				return enviou;
			}
                        
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

		os.close(); // fecha o stream de envio

		return enviou;

	}

	public static Segmento receber(DatagramSocket socket) {
		Segmento s = new Segmento(0, 0, false, false, false, false, 0);

		// buffer (array) de byte para receber dados
		byte[] recvBuf = new byte[64000];
		// pacote udp com o buffer e o tamanho do buffer
		DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);

		try {

			socket.receive(packet); // recebe o pacote

			// array de bytes com stream
			ByteArrayInputStream byteStream = new ByteArrayInputStream(recvBuf);

			// stream para receber
			ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
			s = (Segmento) is.readObject(); // le e pega o objeto (funciona de forma similar ao mÃ©todo get)

			is.close(); // fecha o stream de entrada (receber)

		} catch (IOException ex) {
			System.out.println("Erro: " + ex.getMessage());
		} catch (ClassNotFoundException exc) {
			System.out.println("Erro: " + exc.getMessage());
		}

		return s; // retorna o segmento recebido
	}

	// ====================== PROCESSO DE TROCA DE SEGMENTOS - FLAG FIN ============================
	public static void finalizar(DatagramSocket socket, InetAddress IPAddress) throws Exception {
		System.out.println("\nIniciando a finalizacao da conexao...");

		// DEFINIR O seqNUM DO ESQUEMA DO FIN
		// seqNumClient += 1;

		Segmento sFin = new Segmento(seqNumClient, ackNumClient, false, false, true, false, 0); // primeiro segmento FIN

		if (sFin.isFin) {
			enviar(sFin, socket, IPAddress);
			System.out.println("\nFIN Enviado!");
			System.out.println("\nFIN => " + sFin.toString());
		}

		Segmento sAckFin = receber(socket); // recebe o FIN + ACK do server

		if (sAckFin.isAck && sAckFin.isFin) {
			System.out.println("\nFIN + ACK => " + sAckFin.toString());

			seqNumClient = sAckFin.ackNum;
			ackNumClient = sAckFin.seqNum + 1;
			// DEFINIR O seqNUM e o ackNUM DO ESQUEMA DO FIN

			Segmento sAckF = new Segmento(seqNumClient, ackNumClient, false, true, false, false, 0); // envia ACK

			if (!sAckF.isSyn && !sAckF.isFin && sAckF.isAck) {
				enviar(sAckF, socket, IPAddress); // ENVIA ACK PARA O SERVER
				System.out.println("\nACK Enviado!");
				System.out.println("\nACK => " + sAckF.toString());
				encerraConexao(sAckFin, socket); // encerra com ACK + FIN QUE RECEBEU DO SERVIDOR
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
		if (se.isAck && se.isFin) { // verificando se o segmento que recebeu do server ta com ACK e FIN ativo
			socket.close();
			System.out.println("\nConexao encerrada. (FIN)");
		} else if (se.isRst) {
			socket.close();
			System.out.println("\nConexao encerrada. (RST)");
		} else {
			System.out.println("\nHouve um erro ao tentar encerrar a conexao.");
		}
	}

	// um mÃ©todo recursivo para a retransmissao
	public static boolean retransmitir(Segmento s, DatagramSocket socket, InetAddress IPAddress, Segmento backup,
			int vezes) throws Exception {
		boolean retransmitiu = false;

		if (vezes == 0) { // caso exceda o numero maximo de tentativas (5)
			System.out.println("\nA retransmissao excedeu o numero de tentativas. Descartando pacote. . .");
			return retransmitiu;
		}

		System.out.println("Numero de tentativas restantes: " + vezes);
		boolean enviou = enviar(s, socket, IPAddress);

		if (!enviou) {
			s = new Segmento(seqNumClient, ackNumClient, backup.isSyn, backup.isAck, backup.isFin, backup.isRst,
					backup.dados); // backup para tratamento de um bug
			return retransmitir(s, socket, IPAddress, backup, vezes - 1); // diminui as vezes

		} else if (enviou) {
			System.out.println("\nRetransmissao concluida!");
                        TIMEOUT = 1.5; // timeout volta ao valor inicial
			retransmitiu = true;
		}

		return retransmitiu;
	}

	// ==================== FLAG RST ===================

	/*
	 * A flag RST eh responsavel por encerrar a conexao abruptamente SEM
	 * confirmaçao. Eh enviado nas ocasiões que indicam algo errado.
	 * 
	 */

	// Cliente ENVIA segmento RST
	public static void enviarRST(DatagramSocket socket, InetAddress IPAddress) throws Exception {

		seqNumClient += 1;
		// ackNumClient = 0;

		Segmento sRstClient = new Segmento(seqNumClient, ackNumClient, false, false, false, true, 0);

		if (sRstClient.isRst) {
			enviar(sRstClient, socket, IPAddress);
			System.out.println("\nRST Enviado!");
			System.out.println("\nRST => " + sRstClient.toString());

			encerraConexao(sRstClient, socket); // encerra a conexao imediatamente sem precisar de resposta

		} else {
			System.out.println("Algo errado... Segmento RST nao foi enviado.");
		}
	}

	// Cliente RECEBE segmento RST e encerra a conexao
	public static void receberRST(DatagramSocket socket, InetAddress IPAddress, Segmento sRstServer) throws Exception {
		if (sRstServer.isRst) {
			System.out.println("\nRST do Servidor => " + sRstServer.toString());
			encerraConexao(sRstServer, socket); // encerra imediatamente

		} else {
			System.out.println("Algo errado... Segmento RST nao foi recebido.");

		}
	}

}