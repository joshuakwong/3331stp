## STP

Using UDP sockets, create a protocol named Simple Transfer Protocol (STP) that behaves like TCP.

The STP must include 3 way handshake, and must be able to detect any corrupted packets.

Since the sender and receiver are both located on the same machine, there chances of having dropped / corrupted packets are extremely low. Hence I have included custom module that introduces random error.
