import struct, time, sys
from minecraft.networking.packets import Packet, clientbound, serverbound, PacketBuffer, AbstractPluginMessagePacket
from minecraft.networking.connection import Connection

TARGET = "www.example.com"

conn = Connection("127.0.0.1", username = "Test", port=10001)

def handler(packet):
    if packet.channel.startswith("tunnel-"):
        command = packet.channel[7:]
        if command == "data":
            print("New data: " + repr(packet.data[8:]))
        if command == "success":
            cid = struct.unpack(">Q", packet.data[8:])[0]
            print(f"Connected! ID: {cid}")
            print("Sent http request")
            conn.write_packet(serverbound.play.PluginMessagePacket(channel = "tunnel-data", data = packet.data[8:] + f"GET / HTTP/1.1\r\nHost: {TARGET}\r\nConnection: close\r\n\r\n".encode()))
        if command == "failed":
            print("Connect failed! reason: " + packet.data[12:].decode("UTF-8"))

conn.register_packet_listener(handler,AbstractPluginMessagePacket)

def print_incoming(packet):
    if isinstance(packet, AbstractPluginMessagePacket):
        print('--> %s' % packet, file=sys.stderr)

def print_outgoing(packet):
    if isinstance(packet, AbstractPluginMessagePacket):
        print('<-- %s' % packet, file=sys.stderr)

def handle_join_game(join_game_packet):
    conn.write_packet(serverbound.play.PluginMessagePacket(channel = "tunnel-connect", data = b'\x00' * 8 + struct.pack(">H", 80) + TARGET.encode()))

conn.register_packet_listener(
            print_incoming, Packet, early=True)
conn.register_packet_listener(
            print_outgoing, Packet, outgoing=True)
conn.register_packet_listener(
        handle_join_game, clientbound.play.JoinGamePacket)

conn.connect()

while True:
    time.sleep(1)