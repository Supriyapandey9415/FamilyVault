import { useEffect, useRef, useState } from 'react';
import { Bot, MessageCircle, Send, ShieldCheck, Sparkles, X } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { askAiAssistant } from '../services/chatService';
import './ChatbotWidget.css';

const createMessage = (sender, text, extra = {}) => ({
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    sender,
    text,
    ...extra,
});

const ChatbotWidget = ({ familyName }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [input, setInput] = useState('');
    const [isTyping, setIsTyping] = useState(false);
    const [messages, setMessages] = useState(() => [
        createMessage(
            'ai',
            'Hi! I’m your FamilyVault assistant. Ask me about documents, memories, or family details.'
        ),
    ]);
    const messagesEndRef = useRef(null);
    const inputRef = useRef(null);

    useEffect(() => {
        if (!isOpen) return;
        const frame = window.requestAnimationFrame(() => {
            messagesEndRef.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'end',
            });
        });
        return () => window.cancelAnimationFrame(frame);
    }, [messages, isOpen, isTyping]);

    useEffect(() => {
        if (isOpen) {
            inputRef.current?.focus();
        }
    }, [isOpen]);

    const sendMessage = async () => {
        const message = input.trim();
        if (!message || isTyping) return;

        setMessages((current) => [...current, createMessage('user', message)]);
        setInput('');
        setIsTyping(true);

        try {
            const response = await askAiAssistant(message);
            setMessages((current) => [
                ...current,
                createMessage('ai', response?.reply || 'I could not prepare a response. Please try again.'),
            ]);
        } catch (error) {
            const errorMessage = error.response?.data?.message
                || 'I could not connect to FamilyVault right now. Please try again.';
            setMessages((current) => [
                ...current,
                createMessage('ai', errorMessage, { error: true }),
            ]);
        } finally {
            setIsTyping(false);
        }
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        sendMessage();
    };

    const handleInputKeyDown = (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    };

    return (
        <div className={`chatbot-widget ${isOpen ? 'open' : ''}`}>
            {isOpen && (
                <section className="chatbot-panel" aria-label="FamilyVault AI assistant">
                    <header className="chatbot-header">
                        <div className="chatbot-header-icon">
                            <Bot size={21} />
                        </div>
                        <div className="chatbot-header-copy">
                            <div className="chatbot-title-row">
                                <h2>Vault Assistant</h2>
                                <span className="chatbot-ai-badge"><Sparkles size={11} /> AI</span>
                            </div>
                            <p>
                                <span className="chatbot-status-dot" />
                                {familyName ? `Ready for ${familyName}` : 'Ready to help'}
                            </p>
                        </div>
                        <button
                            type="button"
                            className="chatbot-close"
                            onClick={() => setIsOpen(false)}
                            aria-label="Close assistant"
                        >
                            <X size={19} />
                        </button>
                    </header>

                    <div className="chatbot-security-note">
                        <ShieldCheck size={14} />
                        <span>Your assistant uses your authenticated FamilyVault session.</span>
                    </div>

                    <div className="chatbot-history" aria-live="polite">
                        {messages.map((message) => (
                            <div key={message.id} className={`chatbot-message-row ${message.sender}`}>
                                {message.sender === 'ai' && (
                                    <span className="chatbot-message-avatar"><Bot size={15} /></span>
                                )}
                                <div className={`chatbot-message ${message.error ? 'error' : ''}`}>
                                    {message.sender === 'ai' ? (
                                        <ReactMarkdown
                                            remarkPlugins={[remarkGfm]}
                                            components={{
                                                a: ({ href, children }) => (
                                                    <a href={href} target="_blank" rel="noreferrer">
                                                        {children}
                                                    </a>
                                                ),
                                            }}
                                        >
                                            {message.text}
                                        </ReactMarkdown>
                                    ) : message.text}
                                </div>
                            </div>
                        ))}

                        {isTyping && (
                            <div className="chatbot-message-row ai">
                                <span className="chatbot-message-avatar"><Bot size={15} /></span>
                                <div className="chatbot-message chatbot-typing" role="status">
                                    <span className="chatbot-typing-label">AI is typing</span>
                                    <span className="chatbot-typing-dots" aria-hidden="true">
                                        <span /><span /><span />
                                    </span>
                                </div>
                            </div>
                        )}
                        <div ref={messagesEndRef} className="chatbot-scroll-anchor" aria-hidden="true" />
                    </div>

                    <form className="chatbot-composer" onSubmit={handleSubmit}>
                        <div className="chatbot-input-wrap">
                            <textarea
                                ref={inputRef}
                                value={input}
                                onChange={(event) => setInput(event.target.value)}
                                onKeyDown={handleInputKeyDown}
                                placeholder="Ask about your family vault…"
                                maxLength={1000}
                                rows={1}
                                disabled={isTyping}
                                aria-label="Message the FamilyVault assistant"
                            />
                            <button
                                type="submit"
                                className="chatbot-send"
                                disabled={!input.trim() || isTyping}
                                aria-label="Send message"
                            >
                                <Send size={17} />
                            </button>
                        </div>
                        <p>AI-generated answers may contain mistakes. Verify important information.</p>
                    </form>
                </section>
            )}

            <button
                type="button"
                className="chatbot-launcher"
                onClick={() => setIsOpen((current) => !current)}
                aria-label={isOpen ? 'Close FamilyVault assistant' : 'Open FamilyVault assistant'}
                aria-expanded={isOpen}
            >
                {isOpen ? <X size={23} /> : <MessageCircle size={25} />}
                {!isOpen && <span className="chatbot-launcher-status" />}
            </button>
        </div>
    );
};

export default ChatbotWidget;
